package com.yufesta.domain.match.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.service.MatchRoundAdminService;
import com.yufesta.domain.match.service.MatchRoundBatchService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoundSchedulerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime OPEN_1 = LocalDateTime.of(2026, 9, 25, 0, 0);
    private static final LocalDateTime CLOSE_1 = LocalDateTime.of(2026, 10, 2, 15, 50);
    private static final LocalDateTime PUBLISH_1 = LocalDateTime.of(2026, 10, 2, 16, 0);
    private static final LocalDateTime CLOSE_2 = LocalDateTime.of(2026, 10, 2, 19, 50);
    private static final LocalDateTime PUBLISH_2 = LocalDateTime.of(2026, 10, 2, 20, 0);

    @Mock
    private MatchRoundAdminService matchRoundAdminService;

    @Mock
    private MatchRoundBatchService matchRoundBatchService;

    @Test
    void open_at이_지난_SCHEDULED_회차를_연다() {
        RoundScheduler scheduler = schedulerAt(OPEN_1);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(
                round1(RoundStatus.SCHEDULED, null), round2(RoundStatus.SCHEDULED)));

        scheduler.tick();

        verify(matchRoundAdminService).open(1L);
        verify(matchRoundBatchService, never()).close(any());
        verify(matchRoundBatchService, never()).publish(any());
    }

    @Test
    void close_at이_지난_OPEN_회차를_마감하고_배치한다() {
        RoundScheduler scheduler = schedulerAt(CLOSE_1);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(
                round1(RoundStatus.OPEN, null), round2(RoundStatus.SCHEDULED)));

        scheduler.tick();

        verify(matchRoundBatchService).close(1L);
        verify(matchRoundAdminService, never()).open(any());
    }

    @Test
    void publish_at이_지난_CLOSED_회차를_발표한다() {
        RoundScheduler scheduler = schedulerAt(PUBLISH_1.plusSeconds(3));
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(
                round1(RoundStatus.CLOSED, CLOSE_1.plusSeconds(20)), round2(RoundStatus.SCHEDULED)));

        scheduler.tick();

        verify(matchRoundBatchService).publish(1L);
    }

    @Test
    void 시각_전이면_아무_것도_하지_않는다() {
        RoundScheduler scheduler = schedulerAt(CLOSE_1.minusSeconds(1));
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(
                round1(RoundStatus.OPEN, null), round2(RoundStatus.SCHEDULED)));

        scheduler.tick();

        verify(matchRoundBatchService, never()).close(any());
        verify(matchRoundAdminService, never()).open(any());
    }

    @Test
    void 한_tick에_한_단계만_실행한다() {
        // 1회차 발표와 2회차 오픈(open_at = 1회차 publish_at)이 같이 도래해도 발표만. 발표가 2회차를 열기 때문
        RoundScheduler scheduler = schedulerAt(PUBLISH_1);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(
                round1(RoundStatus.CLOSED, CLOSE_1.plusSeconds(20)), round2(RoundStatus.SCHEDULED)));

        scheduler.tick();

        verify(matchRoundBatchService).publish(1L);
        verify(matchRoundAdminService, never()).open(2L);
    }

    @Test
    void 배치_미실행_CLOSED는_발표하지_않는다() {
        RoundScheduler scheduler = schedulerAt(PUBLISH_1);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(round1(RoundStatus.CLOSED, null)));

        scheduler.tick();

        verify(matchRoundBatchService, never()).publish(any());
    }

    @Test
    void 발표된_회차는_건너뛰고_다음_회차를_본다() {
        RoundScheduler scheduler = schedulerAt(CLOSE_2);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(
                round1(RoundStatus.PUBLISHED, CLOSE_1.plusSeconds(20)), round2(RoundStatus.OPEN)));

        scheduler.tick();

        verify(matchRoundBatchService).close(2L);
        verify(matchRoundBatchService, never()).publish(any());
    }

    @Test
    void 다른_인스턴스가_먼저_처리했으면_조용히_넘어간다() {
        RoundScheduler scheduler = schedulerAt(CLOSE_1);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(round1(RoundStatus.OPEN, null)));
        when(matchRoundBatchService.close(1L)).thenThrow(new CustomException(ErrorCode.MATCH_ROUND_INVALID_STATUS));

        assertThatCode(scheduler::tick).doesNotThrowAnyException();
    }

    @Test
    void 단계가_실패해도_tick은_던지지_않는다() {
        RoundScheduler scheduler = schedulerAt(PUBLISH_1);
        when(matchRoundAdminService.getRounds()).thenReturn(List.of(round1(RoundStatus.CLOSED, CLOSE_1.plusSeconds(20))));
        when(matchRoundBatchService.publish(1L)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(scheduler::tick).doesNotThrowAnyException();
    }

    private RoundScheduler schedulerAt(LocalDateTime now) {
        return new RoundScheduler(matchRoundAdminService, matchRoundBatchService,
                Clock.fixed(now.atZone(KST).toInstant(), KST));
    }

    private static AdminMatchRoundResponse round1(RoundStatus status, LocalDateTime executedAt) {
        return AdminMatchRoundResponse.builder()
                .id(1L).seq(1).status(status)
                .openAt(OPEN_1).closeAt(CLOSE_1).publishAt(PUBLISH_1)
                .executedAt(executedAt)
                .publishedAt(status == RoundStatus.PUBLISHED ? PUBLISH_1 : null)
                .build();
    }

    private static AdminMatchRoundResponse round2(RoundStatus status) {
        return AdminMatchRoundResponse.builder()
                .id(2L).seq(2).status(status)
                .openAt(PUBLISH_1).closeAt(CLOSE_2).publishAt(PUBLISH_2)
                .build();
    }
}
