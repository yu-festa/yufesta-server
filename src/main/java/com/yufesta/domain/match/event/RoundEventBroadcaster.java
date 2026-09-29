package com.yufesta.domain.match.event;

import com.yufesta.common.sse.SseConnectionRegistry;
import com.yufesta.domain.match.enums.RoundStatus;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 회차 이벤트를 이 태스크의 연결들에 <b>한 번씩만</b> 보낸다.
 *
 * <h2>왜 "한 번씩만"이 필요한가</h2>
 * 같은 일이 두 경로로 도착한다.
 * <ol>
 *   <li><b>Redis Pub/Sub</b> — 회차를 바꾼 태스크가 발행하고 모든 태스크가 받는다. 빠르다(1초 안)</li>
 *   <li><b>주기 확인</b>({@code RoundStateWatcher}) — 3초마다 현재 회차 상태를 읽어 바뀌었으면 알린다.
 *       Pub/Sub는 그 순간 연결이 끊겨 있던 구독자에게 다시 보내 주지 않으므로 놓칠 수 있다. 그 안전망이다</li>
 * </ol>
 * 둘 다 정상이면 같은 이벤트가 두 번 온다. 그대로 보내면 1,000명이 두 번 조회한다(발표 순간에 부하가 두 배).
 *
 * <h2>어떻게 가르나</h2>
 * 회차는 앞으로만 가므로 마지막으로 알린 {@link RoundEvent#position() 진행 순번}을 기억해 두고,
 * 그보다 뒤의 일만 보낸다. 먼저 도착한 경로가 보내고 늦게 온 쪽은 버려진다.
 */
@Component
public class RoundEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RoundEventBroadcaster.class);
    private static final int UNKNOWN = -1;
    private static final RoundEventType[] IN_ORDER = {
            RoundEventType.OPENED, RoundEventType.CLOSED, RoundEventType.PUBLISHED
    };

    private final SseConnectionRegistry registry;
    /** 이 태스크가 마지막으로 알린 진행 순번. 기동 직후에는 아직 모른다 */
    private final AtomicInteger lastPosition = new AtomicInteger(UNKNOWN);

    public RoundEventBroadcaster(SseConnectionRegistry registry) {
        this.registry = registry;
    }

    /**
     * 이벤트 하나를 보낸다. 이미 알린 일이거나 그보다 앞선 일이면 보내지 않는다.
     * @return 보냈으면 true
     */
    public boolean broadcast(RoundEvent event) {
        int position = event.position();
        // 두 경로가 동시에 들어와도 하나만 통과한다(값을 올리는 데 성공한 쪽)
        int previous = lastPosition.getAndUpdate(last -> Math.max(last, position));
        if (previous >= position) {
            return false;
        }
        // 발표 신호는 로그인 연결에만 보낸다(SRS 4.3). 결과는 로그인한 사람만 볼 수 있기 때문이다
        registry.broadcast(event.type().eventName(), event.toJson(), event.type() == RoundEventType.PUBLISHED);
        return true;
    }

    /**
     * 지금 회차 상태를 보고, 아직 알리지 않은 일이 있으면 순서대로 알린다(주기 확인이 부른다).
     * <p>예: 마지막으로 알린 것이 "1회차 마감"(12)인데 지금이 "2회차 접수"(21)면
     * 그 사이의 "1회차 발표"(13)와 "2회차 접수"(21)를 차례로 보낸다.
     */
    public void catchUp(int currentSeq, RoundStatus currentStatus) {
        int current = RoundEvent.positionOf(currentSeq, currentStatus);
        int last = lastPosition.get();
        if (last == UNKNOWN) {
            // 기동 직후. 지금 상태를 기준으로만 삼고 알리지 않는다(이미 지난 일이다)
            lastPosition.compareAndSet(UNKNOWN, current);
            return;
        }
        if (current < last) {
            // 회차가 뒤로 갔다. 운영자가 리허설 뒤 초기화한 경우뿐이다. 기준만 다시 잡는다
            log.info("회차 상태가 되돌려졌다. SSE 기준을 다시 잡는다({} → {})", last, current);
            lastPosition.set(current);
            return;
        }
        for (int seq = last / 10; seq <= currentSeq; seq++) {
            for (RoundEventType type : IN_ORDER) {
                RoundEvent event = new RoundEvent(type, seq);
                if (event.position() > last && event.position() <= current && broadcast(event)) {
                    log.info("회차 {} {}: Pub/Sub로 받지 못해 주기 확인이 알렸다", seq, type);
                }
            }
        }
    }
}
