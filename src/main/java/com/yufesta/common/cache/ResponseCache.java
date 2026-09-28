package com.yufesta.common.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 직렬화된 응답 문자열을 Redis에 캐시한다. 부하 테스트에서 처리량 천장이 앱 CPU였고(load/README.md),
 * 요청당 CPU가 Hibernate 매핑 → DTO 변환 → JSON 직렬화에 쓰이므로 <b>완성된 문자열</b>을 캐시해 그 경로를 통째로 건너뛴다.
 * 객체를 캐시하면 꺼낼 때 역직렬화 비용을 다시 내므로 이득이 반으로 준다.
 *
 * <h2>저장 형태</h2>
 * 값 하나에 두 정보를 담는다: {@code <신선 만료 epoch millis>\n<응답 본문>}.
 * Redis 키의 TTL은 {@code 신선 기간 + staleWindow}로 더 길게 잡는다. 그래서 세 상태가 생긴다.
 * <ol>
 *   <li><b>신선</b>(now &lt; freshUntil): 그대로 반환. DB를 건드리지 않는다</li>
 *   <li><b>만료됐지만 살아 있음</b>(freshUntil ≤ now &lt; Redis TTL): 한 요청만 재계산하고
 *       나머지는 옛 값을 그대로 받는다(stale-while-revalidate)</li>
 *   <li><b>없음</b>: 락을 잡은 요청만 계산하고, 못 잡은 요청도 직접 계산한다(첫 요청은 기다리게 하지 않는다)</li>
 * </ol>
 *
 * <h2>스탬피드(cache stampede)</h2>
 * TTL이 끝나는 순간 그 키로 오던 초당 수백 건이 동시에 미스가 되어 전부 DB로 몰리는 현상이다.
 * 570 req/s에서 TTL 2초면 만료마다 수백 건이 한꺼번에 쏟아진다. 두 가지로 막는다.
 * <ul>
 *   <li><b>재계산 락</b>: {@code SET lock:<key> 1 NX PX}로 한 요청만 재계산 권한을 얻고, 끝나면 돌려준다.
 *       락 제한(3초)은 재계산하던 요청이 죽었을 때를 위한 안전망이다</li>
 *   <li><b>만료 지터</b>: 신선 기간에 ±10%를 섞어 여러 키가 같은 순간에 만료되지 않게 흩는다</li>
 * </ul>
 *
 * <h2>장애 격리</h2>
 * Redis가 느리거나 죽어도 API는 살아야 한다. 모든 Redis 호출은 예외를 먹고 원본 계산으로 넘어간다(fail-open).
 * 타임아웃도 200ms로 짧게 잡아 캐시를 기다리다 본 요청이 느려지지 않게 한다(application.yml).
 */
public class ResponseCache {

    private static final Logger log = LoggerFactory.getLogger(ResponseCache.class);
    private static final String KEY_PREFIX = "yufesta";
    private static final String LOCK_PREFIX = "yufesta:lock";
    private static final char SEPARATOR = '\n';
    private static final String PING_KEY = "ping";
    private static final double JITTER = 0.1;

    private final StringRedisTemplate redis;
    private final CacheProperties properties;
    private final Clock clock;

    public ResponseCache(StringRedisTemplate redis, CacheProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 캐시에서 찾고 없으면 {@code loader}로 만들어 저장한 뒤 돌려준다.
     * @param key   CacheKey로 만든 이름(버전·접두사는 여기서 붙인다)
     * @param ttl   신선 기간. 이 시간이 지나면 한 요청만 다시 계산한다
     * @param loader 원본 계산(서비스 조회 + 직렬화). 캐시가 꺼져 있거나 Redis가 죽으면 이것만 쓴다.
     *               null을 반환하면 저장하지 않는다(캐시 대상이 아닌 응답)
     */
    public String get(String key, Duration ttl, Supplier<String> loader) {
        if (!properties.enabled()) {
            return loader.get();
        }
        String fullKey = fullKey(key);
        Entry cached = read(fullKey);

        if (cached != null && cached.isFresh(now())) {
            return cached.payload();
        }
        // 만료됐지만 값이 남아 있으면, 재계산 권한을 못 얻은 요청은 옛 값을 그대로 쓴다.
        // 이 한 줄이 "TTL 만료 순간 전부 DB로 몰리는" 상황을 막는다
        if (cached != null && !tryLock(fullKey)) {
            return cached.payload();
        }
        String fresh = loader.get();
        // loader가 null을 주면 "이 응답은 캐시하면 안 된다"는 뜻이다(예: 200이 아닌 응답)
        if (fresh != null) {
            write(fullKey, fresh, ttl);
        }
        // 옛 값이 있었다면 위에서 락을 잡고 들어온 것이다. 재계산이 끝났으니 돌려준다.
        // 돌려주지 않으면 락 제한(3초)이 끝날 때까지 아무도 재계산하지 못해, 신선 기간이 그보다 짧은 키(요약 2초)는
        // 3초마다 갱신되고 그 사이 요청은 전부 락을 시도했다 실패한다(2026-09-28 측정에서 확인).
        // loader가 예외를 던지면 여기까지 오지 않아 락이 남는다. 의도한 것이다: DB가 아플 때 락 제한이 재시도 간격이 되고
        // 그동안 다른 요청은 옛 값을 받는다
        if (cached != null) {
            unlock(fullKey);
        }
        return fresh;
    }

    /**
     * 커넥션이 살아 있는지 명령 한 번으로 확인하고 성공 여부를 돌려준다. 로그는 남기지 않는다
     * (부르는 쪽이 기동 예열인지 주기 확인인지에 따라 다르게 알려야 하기 때문. {@link CacheConnectionKeeper}).
     * <p>이 메서드가 필요한 이유는 두 가지다.
     * <ul>
     *   <li><b>첫 연결</b> — Redis 커넥션은 요청마다 맺지 않고 하나를 계속 재사용한다. 그 하나를 처음 맺을 때
     *       DNS 조회와 TCP 핸드셰이크가 필요한데, 기동 직후는 CPU가 Spring 초기화에 묶여 있어 느리다.
     *       미리 맺어 두지 않으면 첫 사용자 요청이 그 비용을 내고 타임아웃된다(2026-09-27 배포에서 관측)</li>
     *   <li><b>유휴 끊김</b> — 오래 쓰지 않은 소켓은 중간에서 정리된다. 앱은 끊긴 줄 모르고 있다가
     *       다음 명령에서 실패한다(2026-09-28 08:49 관측). 주기적으로 말을 걸어 두면 생기지 않는다</li>
     * </ul>
     * 캐시가 꺼져 있으면 확인할 커넥션도 없으므로 true를 돌려준다.
     */
    public boolean ping() {
        if (!properties.enabled()) {
            return true;
        }
        try {
            redis.hasKey(fullKey(PING_KEY));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 운영자 쓰기 직후 해당 키를 즉시 버린다. TTL을 기다리지 않게 하는 유일한 방법 */
    public void evict(String... keys) {
        if (!properties.enabled()) {
            return;
        }
        for (String key : keys) {
            try {
                redis.delete(fullKey(key));
            } catch (RuntimeException exception) {
                // 지우지 못해도 TTL이 지나면 사라진다. 운영자 화면이 잠깐 옛 값을 보일 뿐이라 요청을 실패시키지 않는다
                logFailure("evict", key, exception);
            }
        }
    }

    private Entry read(String fullKey) {
        try {
            return Entry.parse(redis.opsForValue().get(fullKey));
        } catch (RuntimeException exception) {
            logFailure("read", fullKey, exception);
            return null;
        }
    }

    private void write(String fullKey, String payload, Duration ttl) {
        try {
            long freshUntil = now() + jittered(ttl).toMillis();
            // Redis TTL은 신선 기간보다 길게 둔다. 그 차이가 stale-while-revalidate로 쓸 수 있는 구간이다
            Duration hold = jittered(ttl).plus(properties.staleWindow());
            redis.opsForValue().set(fullKey, freshUntil + String.valueOf(SEPARATOR) + payload, hold);
        } catch (RuntimeException exception) {
            logFailure("write", fullKey, exception);
        }
    }

    /** SET NX로 재계산 권한을 한 요청에만 준다. 실패(획득 못 함)도 정상 흐름이다 */
    private boolean tryLock(String fullKey) {
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(lockKey(fullKey), "1", properties.lockTimeout());
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException exception) {
            logFailure("lock", fullKey, exception);
            return true; // Redis를 못 쓰면 각자 계산한다(캐시가 없는 것과 같은 상태)
        }
    }

    // 남의 락을 지울 수 있다(내 재계산이 락 제한보다 오래 걸려 다른 요청이 이미 새로 잡은 경우).
    // 그래도 재계산이 한 번 더 도는 것뿐이라 소유 확인 없이 지운다. 이 락은 정확성이 아니라 효율을 위한 것이다
    private void unlock(String fullKey) {
        try {
            redis.delete(lockKey(fullKey));
        } catch (RuntimeException exception) {
            logFailure("unlock", fullKey, exception);
        }
    }

    private static String lockKey(String fullKey) {
        return LOCK_PREFIX + ":" + fullKey;
    }

    // 여러 키가 같은 순간에 만료되면 그 순간에 부하가 몰린다. ±10%로 흩는다
    private static Duration jittered(Duration ttl) {
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-JITTER, JITTER);
        return Duration.ofMillis(Math.max(1, (long) (ttl.toMillis() * factor)));
    }

    private String fullKey(String key) {
        return KEY_PREFIX + ":" + properties.version() + ":" + key;
    }

    private long now() {
        return clock.millis();
    }

    private static void logFailure(String operation, String key, RuntimeException exception) {
        // 캐시 실패는 기능 실패가 아니다. 경고로만 남기고 흐름을 막지 않는다
        log.warn("응답 캐시 {} 실패 (key={}): {}", operation, key, exception.getMessage());
    }

    /** 저장 형태 {@code <freshUntil>\n<payload>} 를 다루는 값 객체 */
    private record Entry(long freshUntil, String payload) {

        static Entry parse(String raw) {
            if (raw == null) {
                return null;
            }
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                return null; // 형식이 다르면(옛 버전 잔재) 없는 것으로 본다
            }
            try {
                return new Entry(Long.parseLong(raw.substring(0, separator)), raw.substring(separator + 1));
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        boolean isFresh(long now) {
            return now < freshUntil;
        }
    }
}
