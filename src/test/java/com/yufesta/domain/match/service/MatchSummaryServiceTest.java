package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.cache.CacheProperties;
import com.yufesta.common.cache.ResponseCache;
import com.yufesta.domain.match.dto.response.MatchSummaryCommonResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MatchSummaryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 15, 30);
    private static final LocalDateTime PUBLISH_1 = LocalDateTime.of(2026, 10, 2, 16, 0);
    private static final LocalDateTime PUBLISH_2 = LocalDateTime.of(2026, 10, 2, 20, 0);
    private static final long USER_ID = 7L;
    private static final String SUMMARY_KEY = "yufesta:v1:match:summary";

    @Mock
    private MatchRoundService matchRoundService;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Clock fixedClock;
    private MatchSummaryService matchSummaryService;
    private MatchRound first;
    private MatchRound second;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        // 기본은 캐시를 끈 상태다. 캐시가 없을 때의 동작이 기준이고, 캐시는 그 위에 얹히는 것이기 때문
        matchSummaryService = serviceWith(cache(false));
        first = round(1L, 1, PUBLISH_1);
        second = round(2L, 2, PUBLISH_2);
    }

    @Test
    void 비로그인이면_서버_시각과_회차만_주고_my는_null이다() {
        first.open();
        when(matchRoundService.getCurrentRound()).thenReturn(first);
        when(matchRoundService.findNextRound(first)).thenReturn(Optional.of(second));
        when(applicationRepository.countByRound_IdAndCanceledAtIsNull(1L)).thenReturn(137L);

        MatchSummaryResponse summary = matchSummaryService.getSummary(null);

        assertThat(summary.serverNow()).isEqualTo(NOW);
        assertThat(summary.currentRound().seq()).isEqualTo(1);
        assertThat(summary.currentRound().status()).isEqualTo(RoundStatus.OPEN);
        assertThat(summary.nextRound().seq()).isEqualTo(2);
        assertThat(summary.applicantCount()).isEqualTo(137L);
        assertThat(summary.my()).isNull();
    }

    @Test
    void 로그인_사용자는_현재_회차_신청_여부를_받고_발표_전이면_결과가_없다() {
        first.open();
        when(matchRoundService.getCurrentRound()).thenReturn(first);
        when(matchRoundService.findNextRound(first)).thenReturn(Optional.of(second));
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.empty());
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 1L)).thenReturn(Optional.of(application(11L, first)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.my().applied()).isTrue();
        assertThat(summary.my().lastResult()).isNull();
    }

    @Test
    void 취소한_신청은_신청한_것으로_보지_않는다() {
        first.open();
        Application canceled = application(11L, first);
        canceled.cancel(NOW);
        when(matchRoundService.getCurrentRound()).thenReturn(first);
        when(matchRoundService.findNextRound(first)).thenReturn(Optional.of(second));
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.empty());
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 1L)).thenReturn(Optional.of(canceled));

        assertThat(matchSummaryService.getSummary(USER_ID).my().applied()).isFalse();
    }

    @Test
    void 일회차_발표_후에는_이회차가_현재이고_일회차_결과가_lastResult로_온다() {
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        second.open();
        Application firstApplication = application(11L, first);
        when(matchRoundService.getCurrentRound()).thenReturn(second);
        when(matchRoundService.findNextRound(second)).thenReturn(Optional.empty());
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(first));
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 2L)).thenReturn(Optional.empty());
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 1L)).thenReturn(Optional.of(firstApplication));
        when(matchRepository.existsByApplication_Id(11L)).thenReturn(true);

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.currentRound().seq()).isEqualTo(2);
        assertThat(summary.nextRound()).isNull();
        assertThat(summary.my().applied()).isFalse();
        assertThat(summary.my().lastResult().roundSeq()).isEqualTo(1);
        assertThat(summary.my().lastResult().status()).isEqualTo(MatchResultStatus.MATCHED);
    }

    @Test
    void 발표된_회차에_신청했지만_매칭이_없으면_UNMATCHED다() {
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        second.open();
        when(matchRoundService.getCurrentRound()).thenReturn(second);
        when(matchRoundService.findNextRound(second)).thenReturn(Optional.empty());
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(first));
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 2L)).thenReturn(Optional.empty());
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 1L)).thenReturn(Optional.of(application(11L, first)));
        when(matchRepository.existsByApplication_Id(11L)).thenReturn(false);

        assertThat(matchSummaryService.getSummary(USER_ID).my().lastResult().status())
                .isEqualTo(MatchResultStatus.UNMATCHED);
    }

    @Test
    void 발표된_회차에_신청하지_않았으면_lastResult가_null이다() {
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        second.open();
        when(matchRoundService.getCurrentRound()).thenReturn(second);
        when(matchRoundService.findNextRound(second)).thenReturn(Optional.empty());
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(first));
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 2L)).thenReturn(Optional.empty());
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 1L)).thenReturn(Optional.empty());

        assertThat(matchSummaryService.getSummary(USER_ID).my().lastResult()).isNull();
    }

    @Test
    void 캐시에_공통부가_있으면_회차와_신청자_수를_DB에서_읽지_않는다() {
        first.open();
        matchSummaryService = serviceWith(cache(true));
        given캐시에(MatchSummaryCommonResponse.of(first, second, 137L));

        MatchSummaryResponse summary = matchSummaryService.getSummary(null);

        assertThat(summary.currentRound().seq()).isEqualTo(1);
        assertThat(summary.nextRound().seq()).isEqualTo(2);
        assertThat(summary.applicantCount()).isEqualTo(137L);
        verify(matchRoundService, never()).getCurrentRound();
        verify(applicationRepository, never()).countByRound_IdAndCanceledAtIsNull(anyLong());
    }

    @Test
    void 캐시가_맞아도_서버_시각과_내_상태는_요청마다_계산한다() {
        first.open();
        matchSummaryService = serviceWith(cache(true));
        given캐시에(MatchSummaryCommonResponse.of(first, second, 137L));
        when(matchRoundService.getCurrentRound()).thenReturn(first);
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.empty());
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, 1L)).thenReturn(Optional.of(application(11L, first)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.serverNow()).isEqualTo(NOW);
        assertThat(summary.my().applied()).isTrue();
    }

    @Test
    void 캐시에_읽을_수_없는_값이_있으면_DB로_계산한다() {
        first.open();
        matchSummaryService = serviceWith(cache(true));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(SUMMARY_KEY)).thenReturn(freshUntil() + "\n{깨진 값");
        when(matchRoundService.getCurrentRound()).thenReturn(first);
        when(matchRoundService.findNextRound(first)).thenReturn(Optional.of(second));
        when(applicationRepository.countByRound_IdAndCanceledAtIsNull(1L)).thenReturn(5L);

        assertThat(matchSummaryService.getSummary(null).applicantCount()).isEqualTo(5L);
    }

    // DB 조회 빈은 실제 객체를 쓴다. 두 클래스는 트랜잭션 경계 때문에 나뉜 것이고 동작은 하나의 흐름이라 함께 검증한다
    private MatchSummaryService serviceWith(ResponseCache responseCache) {
        MatchSummaryQueryService queryService =
                new MatchSummaryQueryService(matchRoundService, applicationRepository, matchRepository);
        return new MatchSummaryService(queryService, responseCache, objectMapper, fixedClock);
    }

    private ResponseCache cache(boolean enabled) {
        return new ResponseCache(redis, new CacheProperties(enabled, "v1", Duration.ofSeconds(10), Duration.ofSeconds(3)), fixedClock);
    }

    private void given캐시에(MatchSummaryCommonResponse common) {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(SUMMARY_KEY)).thenReturn(freshUntil() + "\n" + objectMapper.writeValueAsString(common));
    }

    private long freshUntil() {
        return NOW.atZone(KST).toInstant().toEpochMilli() + 5_000;
    }

    private static MatchRound round(Long id, int seq, LocalDateTime publishAt) {
        MatchRound round = MatchRound.builder()
                .seq(seq)
                .openAt(publishAt.minusDays(7))
                .closeAt(publishAt.minusMinutes(10))
                .publishAt(publishAt)
                .build();
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    private static Application application(Long id, MatchRound round) {
        Application application = Application.builder()
                .round(round)
                .instagramId("insta")
                .nickname("닉네임")
                .gender(Gender.F)
                .termsVersion("v1")
                .privacyVersion("v1")
                .ageConfirmed(true)
                .agreedAt(NOW)
                .build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }
}
