package com.yufesta.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 캐시의 세 가지 핵심 동작을 고정한다: 신선한 값 재사용, 스탬피드 방지(만료 시 한 번만 재계산), 장애 시 우회
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResponseCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private static final String KEY = "timetable";
    private static final String FULL_KEY = "yufesta:v1:timetable";
    private static final String LOCK_KEY = "yufesta:lock:yufesta:v1:timetable";
    private static final Duration TTL = Duration.ofSeconds(10);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ResponseCache cache;
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        cache = new ResponseCache(redis, properties(true), clock);
    }

    @Test
    void 신선한_값이_있으면_원본을_부르지_않는다() {
        long freshUntil = NOW.toEpochMilli() + 5_000;
        when(valueOps.get(FULL_KEY)).thenReturn(freshUntil + "\n{\"slots\":[]}");
        AtomicInteger loaded = new AtomicInteger();

        String result = cache.get(KEY, TTL, () -> {
            loaded.incrementAndGet();
            return "새 값";
        });

        assertThat(result).isEqualTo("{\"slots\":[]}");
        assertThat(loaded).hasValue(0);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void 값이_없으면_원본을_만들어_저장한다() {
        when(valueOps.get(FULL_KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        String result = cache.get(KEY, TTL, () -> "{\"slots\":[1]}");

        assertThat(result).isEqualTo("{\"slots\":[1]}");
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> hold = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(FULL_KEY), value.capture(), hold.capture());
        // 저장 형태: <freshUntil>\n<본문>
        assertThat(value.getValue()).endsWith("\n{\"slots\":[1]}");
        long freshUntil = Long.parseLong(value.getValue().split("\n")[0]);
        assertThat(freshUntil).isBetween(NOW.toEpochMilli() + 9_000, NOW.toEpochMilli() + 11_000); // 지터 ±10%
        // Redis TTL은 신선 기간 + staleWindow(30초)로 더 길다. 그 구간이 stale-while-revalidate용이다
        assertThat(hold.getValue()).isGreaterThan(TTL.plusSeconds(25));
    }

    @Test
    void 만료된_값은_재계산_권한을_얻은_요청만_새로_만들고_나머지는_옛_값을_쓴다() {
        long expired = NOW.toEpochMilli() - 1_000;
        when(valueOps.get(FULL_KEY)).thenReturn(expired + "\n옛 값");
        // 첫 요청만 락을 얻고 두 번째는 실패한다
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true, false);
        AtomicInteger loaded = new AtomicInteger();

        String winner = cache.get(KEY, TTL, () -> {
            loaded.incrementAndGet();
            return "새 값";
        });
        String loser = cache.get(KEY, TTL, () -> {
            loaded.incrementAndGet();
            return "새 값";
        });

        assertThat(winner).isEqualTo("새 값");
        assertThat(loser).isEqualTo("옛 값");   // 만료됐지만 살아 있는 값으로 응답
        assertThat(loaded).hasValue(1);        // 원본 계산은 한 번만
    }

    @Test
    void 재계산이_끝나면_락을_돌려준다() {
        long expired = NOW.toEpochMilli() - 1_000;
        when(valueOps.get(FULL_KEY)).thenReturn(expired + "\n옛 값");
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        cache.get(KEY, TTL, () -> "새 값");

        // 돌려주지 않으면 신선 기간이 락 제한보다 짧은 키가 락 제한 주기로만 갱신된다
        verify(redis).delete(LOCK_KEY);
    }

    @Test
    void 원본_계산이_실패하면_락을_남겨_재시도_간격으로_쓴다() {
        long expired = NOW.toEpochMilli() - 1_000;
        when(valueOps.get(FULL_KEY)).thenReturn(expired + "\n옛 값");
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> cache.get(KEY, TTL, () -> {
            throw new IllegalStateException("DB 장애");
        })).isInstanceOf(IllegalStateException.class);

        verify(redis, never()).delete(LOCK_KEY);
    }

    @Test
    void 값이_없어_락_없이_계산한_요청은_락을_건드리지_않는다() {
        when(valueOps.get(FULL_KEY)).thenReturn(null);

        cache.get(KEY, TTL, () -> "새 값");

        verify(redis, never()).delete(LOCK_KEY);
    }

    @Test
    void Redis가_죽어도_원본_경로로_응답한다() {
        when(valueOps.get(FULL_KEY)).thenThrow(new RedisConnectionFailureException("연결 실패"));

        String result = cache.get(KEY, TTL, () -> "{\"ok\":true}");

        assertThat(result).isEqualTo("{\"ok\":true}");
    }

    @Test
    void 캐시를_끄면_Redis를_전혀_건드리지_않는다() {
        ResponseCache disabled = new ResponseCache(redis, properties(false), clock);

        String result = disabled.get(KEY, TTL, () -> "본문");
        disabled.evict(KEY);

        assertThat(result).isEqualTo("본문");
        verify(valueOps, never()).get(anyString());
        verify(redis, never()).delete(anyString());
    }

    @Test
    void 무효화는_버전_접두사가_붙은_키를_지우고_실패해도_예외를_던지지_않는다() {
        when(redis.delete(FULL_KEY)).thenThrow(new RedisConnectionFailureException("연결 실패"));

        cache.evict(KEY);

        verify(redis).delete(FULL_KEY);
    }

    @Test
    void 커넥션_확인은_실패를_예외가_아니라_false로_알린다() {
        when(redis.hasKey("yufesta:v1:ping")).thenThrow(new RedisConnectionFailureException("연결 실패"));

        assertThat(cache.ping()).isFalse();   // 예외가 새면 기동이 막힌다

        verify(redis).hasKey("yufesta:v1:ping");
    }

    @Test
    void 커넥션이_살아_있으면_true다() {
        when(redis.hasKey("yufesta:v1:ping")).thenReturn(false);

        assertThat(cache.ping()).isTrue();
    }

    @Test
    void 캐시가_꺼져_있으면_확인할_커넥션도_없다() {
        assertThat(new ResponseCache(redis, properties(false), clock).ping()).isTrue();

        verify(redis, org.mockito.Mockito.never()).hasKey(anyString());
    }

    private static CacheProperties properties(boolean enabled) {
        return new CacheProperties(enabled, "v1", Duration.ofSeconds(30), Duration.ofSeconds(3));
    }
}
