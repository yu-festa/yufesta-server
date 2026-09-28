package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yufesta.common.cache.CacheProperties;
import com.yufesta.common.cache.ResponseCache;
import com.yufesta.domain.match.dto.response.MatchSummaryCommonResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MyApplicationState;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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

/**
 * 홈 요약의 조립을 검증한다. 취소·매칭 여부를 가르는 쿼리 자체는 ApplicationRepositoryTest가 실제 DB로 본다
 */
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
        given일회차_접수_중(137L);

        MatchSummaryResponse summary = matchSummaryService.getSummary(null);

        assertThat(summary.serverNow()).isEqualTo(NOW);
        assertThat(summary.currentRound().seq()).isEqualTo(1);
        assertThat(summary.currentRound().status()).isEqualTo(RoundStatus.OPEN);
        assertThat(summary.nextRound().seq()).isEqualTo(2);
        assertThat(summary.applicantCount()).isEqualTo(137L);
        assertThat(summary.my()).isNull();
        verify(applicationRepository, never()).findMyStates(anyLong(), org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void 로그인_사용자는_현재_회차_신청_여부를_받고_발표_전이면_결과가_없다() {
        first.open();
        given일회차_접수_중(1L);
        // 발표된 회차가 없으니 현재 회차만 묻는다
        when(applicationRepository.findMyStates(USER_ID, List.of(1L)))
                .thenReturn(List.of(new MyApplicationState(1L, false)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.my().applied()).isTrue();
        assertThat(summary.my().lastResult()).isNull();
    }

    @Test
    void 유효한_신청이_없으면_신청하지_않은_것으로_본다() {
        // 취소한 신청은 쿼리가 걸러 행이 오지 않는다
        first.open();
        given일회차_접수_중(0L);
        when(applicationRepository.findMyStates(USER_ID, List.of(1L))).thenReturn(List.of());

        assertThat(matchSummaryService.getSummary(USER_ID).my().applied()).isFalse();
    }

    @Test
    void 일회차_발표_후에는_이회차가_현재이고_일회차_결과가_lastResult로_온다() {
        given일회차_발표_이회차_접수_중();
        when(applicationRepository.findMyStates(USER_ID, List.of(2L, 1L)))
                .thenReturn(List.of(new MyApplicationState(1L, true)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.currentRound().seq()).isEqualTo(2);
        assertThat(summary.nextRound()).isNull();
        assertThat(summary.my().applied()).isFalse();
        assertThat(summary.my().lastResult().roundSeq()).isEqualTo(1);
        assertThat(summary.my().lastResult().status()).isEqualTo(MatchResultStatus.MATCHED);
    }

    @Test
    void 발표된_회차에_신청했지만_매칭이_없으면_UNMATCHED다() {
        given일회차_발표_이회차_접수_중();
        when(applicationRepository.findMyStates(USER_ID, List.of(2L, 1L)))
                .thenReturn(List.of(new MyApplicationState(1L, false)));

        assertThat(matchSummaryService.getSummary(USER_ID).my().lastResult().status())
                .isEqualTo(MatchResultStatus.UNMATCHED);
    }

    @Test
    void 발표된_회차에_신청하지_않았으면_lastResult가_null이다() {
        given일회차_발표_이회차_접수_중();
        // 2회차에만 신청한 사용자
        when(applicationRepository.findMyStates(USER_ID, List.of(2L, 1L)))
                .thenReturn(List.of(new MyApplicationState(2L, false)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.my().applied()).isTrue();
        assertThat(summary.my().lastResult()).isNull();
    }

    @Test
    void 회차가_전부_발표되면_현재_회차와_발표_회차가_같아_한_회차만_묻는다() {
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        second.open();
        second.close();
        second.publish(PUBLISH_2);
        when(matchRoundService.getCurrentRound()).thenReturn(second);
        when(matchRoundService.findNextRound(second)).thenReturn(Optional.empty());
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(second));
        when(applicationRepository.findMyStates(USER_ID, List.of(2L)))
                .thenReturn(List.of(new MyApplicationState(2L, true)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.my().applied()).isTrue();
        assertThat(summary.my().lastResult().roundSeq()).isEqualTo(2);
        assertThat(summary.my().lastResult().status()).isEqualTo(MatchResultStatus.MATCHED);
    }

    @Test
    void 캐시에_공통부가_있으면_비로그인_요청은_DB를_읽지_않는다() {
        first.open();
        matchSummaryService = serviceWith(cache(true));
        given캐시에(MatchSummaryCommonResponse.of(first, second, null, 137L));

        MatchSummaryResponse summary = matchSummaryService.getSummary(null);

        assertThat(summary.currentRound().seq()).isEqualTo(1);
        assertThat(summary.nextRound().seq()).isEqualTo(2);
        assertThat(summary.applicantCount()).isEqualTo(137L);
        verifyNoInteractions(matchRoundService, applicationRepository);
    }

    @Test
    void 캐시가_맞으면_로그인_요청은_회차를_다시_읽지_않고_내_상태만_쿼리_하나로_읽는다() {
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        second.open();
        matchSummaryService = serviceWith(cache(true));
        given캐시에(MatchSummaryCommonResponse.of(second, null, first, 40L));
        when(applicationRepository.findMyStates(USER_ID, List.of(2L, 1L)))
                .thenReturn(List.of(new MyApplicationState(2L, false), new MyApplicationState(1L, true)));

        MatchSummaryResponse summary = matchSummaryService.getSummary(USER_ID);

        assertThat(summary.serverNow()).isEqualTo(NOW);
        assertThat(summary.my().applied()).isTrue();
        assertThat(summary.my().lastResult().status()).isEqualTo(MatchResultStatus.MATCHED);
        verifyNoInteractions(matchRoundService);
        verify(applicationRepository, never()).countByRound_IdAndCanceledAtIsNull(anyLong());
    }

    @Test
    void 캐시에_읽을_수_없는_값이_있으면_DB로_계산한다() {
        first.open();
        matchSummaryService = serviceWith(cache(true));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(SUMMARY_KEY)).thenReturn(freshUntil() + "\n{깨진 값");
        given일회차_접수_중(5L);

        assertThat(matchSummaryService.getSummary(null).applicantCount()).isEqualTo(5L);
    }

    private void given일회차_접수_중(long applicantCount) {
        when(matchRoundService.getCurrentRound()).thenReturn(first);
        when(matchRoundService.findNextRound(first)).thenReturn(Optional.of(second));
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.empty());
        when(applicationRepository.countByRound_IdAndCanceledAtIsNull(1L)).thenReturn(applicantCount);
    }

    private void given일회차_발표_이회차_접수_중() {
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        second.open();
        when(matchRoundService.getCurrentRound()).thenReturn(second);
        when(matchRoundService.findNextRound(second)).thenReturn(Optional.empty());
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(first));
    }

    // DB 조회 빈은 실제 객체를 쓴다. 두 클래스는 트랜잭션 경계 때문에 나뉜 것이고 동작은 하나의 흐름이라 함께 검증한다
    private MatchSummaryService serviceWith(ResponseCache responseCache) {
        MatchSummaryQueryService queryService = new MatchSummaryQueryService(matchRoundService, applicationRepository);
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
}
