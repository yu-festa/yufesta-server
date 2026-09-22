package com.yufesta.domain.match.scheduler;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.service.MatchRoundAdminService;
import com.yufesta.domain.match.service.MatchRoundBatchService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 회차 시각 자동 실행(FR-MT-02~04, NFR-AV-03). open_at에 OPEN, close_at에 CLOSED + 배치, publish_at에 PUBLISHED + 이월.
 * 운영자 수동 API와 같은 서비스 메서드를 부른다. 다중 인스턴스 중복 실행은 서비스의 회차 행 잠금 + 상태 전이 검사가 막는다(§5)
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class RoundScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoundScheduler.class);

    private final MatchRoundAdminService matchRoundAdminService;
    private final MatchRoundBatchService matchRoundBatchService;
    private final Clock clock;

    public RoundScheduler(
            MatchRoundAdminService matchRoundAdminService,
            MatchRoundBatchService matchRoundBatchService,
            Clock clock
    ) {
        this.matchRoundAdminService = matchRoundAdminService;
        this.matchRoundBatchService = matchRoundBatchService;
        this.clock = clock;
    }

    /**
     * 주기마다 회차를 seq 순으로 훑어 시각이 지난 첫 단계 하나만 실행한다.
     * <p>한 tick에 한 단계만 하는 이유: 발표가 다음 회차를 여는 것처럼 한 단계가 다른 회차의 상태를 바꾸므로,
     * tick 시작 때 읽은 목록으로 다음 단계까지 판단하면 낡은 상태로 움직인다. 밀린 단계(재시작 후 복구 포함)는 다음 tick이 이어서 한다.
     * 실패해도 던지지 않고 로그만 남기며, 다음 tick이 다시 시도한다. 그래도 안 되면 운영자 수동 API가 복구 경로다.
     */
    @Scheduled(fixedDelayString = "${app.scheduler.round-tick:PT10S}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now(clock);
        matchRoundAdminService.getRounds().stream()
                .map(round -> dueStep(round, now))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(this::run);
    }

    // 상태별로 "다음 단계"는 하나뿐이고, 그 단계의 시각이 지났을 때만 실행 대상이다. 시각은 회차 행의 값만 본다
    private Optional<DueStep> dueStep(AdminMatchRoundResponse round, LocalDateTime now) {
        return switch (round.status()) {
            case SCHEDULED -> due(now, round.openAt())
                    ? Optional.of(new DueStep("오픈", round, () -> matchRoundAdminService.open(round.id())))
                    : Optional.empty();
            case OPEN -> due(now, round.closeAt())
                    ? Optional.of(new DueStep("마감·배치", round, () -> matchRoundBatchService.close(round.id())))
                    : Optional.empty();
            case CLOSED -> publishable(round, now)
                    ? Optional.of(new DueStep("발표", round, () -> matchRoundBatchService.publish(round.id())))
                    : Optional.empty();
            case PUBLISHED -> Optional.empty();
        };
    }

    // 배치 결과(executed_at) 없이 발표하면 전원 미매칭이 된다. close()가 전이와 배치를 한 트랜잭션으로 묶어 정상 경로에서는 생기지 않는 상태
    private boolean publishable(AdminMatchRoundResponse round, LocalDateTime now) {
        if (!due(now, round.publishAt())) {
            return false;
        }
        if (round.executedAt() == null) {
            log.warn("회차 {} 발표 시각이 지났지만 배치 미실행. 운영자 확인 필요", round.seq());
            return false;
        }
        return true;
    }

    private static boolean due(LocalDateTime now, LocalDateTime at) {
        return !now.isBefore(at);
    }

    private void run(DueStep step) {
        int seq = step.round().seq();
        try {
            step.action().run();
            log.info("회차 {} {} 완료(스케줄러)", seq, step.name());
        } catch (CustomException exception) {
            // 다른 인스턴스가 먼저 처리한 경우: 행 잠금에서 기다린 뒤 전이 검사에 걸린다. 정상이므로 조용히 넘어간다(§5)
            if (exception.getErrorCode() == ErrorCode.MATCH_ROUND_INVALID_STATUS) {
                log.info("회차 {} {} 건너뜀: 이미 처리됨", seq, step.name());
                return;
            }
            log.error("회차 {} {} 실패({}). 운영자 수동 실행 필요", seq, step.name(), exception.getErrorCode().code(), exception);
        } catch (RuntimeException exception) {
            log.error("회차 {} {} 실패. 운영자 수동 실행 필요", seq, step.name(), exception);
        }
    }

    private record DueStep(String name, AdminMatchRoundResponse round, Runnable action) {
    }
}
