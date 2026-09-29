package com.yufesta.domain.match.scheduler;

import com.yufesta.domain.match.dto.response.MatchRoundResponse;
import com.yufesta.domain.match.event.RoundEventBroadcaster;
import com.yufesta.domain.match.service.MatchSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 3초마다 현재 회차 상태를 읽어, Pub/Sub로 받지 못한 변화가 있으면 연결들에 알린다.
 *
 * <p>Pub/Sub는 "지금 듣고 있는 구독자"에게만 전달한다. 그 순간 Redis 연결이 끊겨 있었다면 메시지는 사라지고
 * 다시 오지 않는다. 발표는 하루에 두 번뿐인 이벤트라 한 번 놓치면 그 태스크에 붙은 사용자는 발표를 모른다.
 * 그래서 빠른 경로(Pub/Sub)와 놓치지 않는 경로(주기 확인)를 함께 둔다.
 *
 * <p>상태는 홈 요약과 같은 값(캐시된 공통부)을 읽는다. 캐시가 맞으면 DB를 읽지 않으므로 3초 주기의 부담은 없고,
 * Redis가 죽어 있으면 캐시가 DB 경로로 넘어가(fail-open) 이 확인은 계속 동작한다.
 */
@Component
@ConditionalOnProperty(name = "app.sse.watch-enabled", havingValue = "true", matchIfMissing = true)
public class RoundStateWatcher {

    private static final Logger log = LoggerFactory.getLogger(RoundStateWatcher.class);

    private final MatchSummaryService matchSummaryService;
    private final RoundEventBroadcaster broadcaster;

    public RoundStateWatcher(MatchSummaryService matchSummaryService, RoundEventBroadcaster broadcaster) {
        this.matchSummaryService = matchSummaryService;
        this.broadcaster = broadcaster;
    }

    @Scheduled(fixedDelayString = "${app.sse.watch-interval:PT3S}")
    public void watch() {
        try {
            MatchRoundResponse current = matchSummaryService.getSummary(null).currentRound();
            broadcaster.catchUp(current.seq(), current.status());
        } catch (RuntimeException exception) {
            // 이번 확인만 건너뛴다. 3초 뒤 다시 본다
            log.warn("회차 상태 확인 실패: {}", exception.getMessage());
        }
    }
}
