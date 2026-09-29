package com.yufesta.domain.match.event;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Redis 구독을 기동 뒤에 시작하고, 붙지 못했으면 붙을 때까지 다시 시도한다.
 *
 * <p>구독을 기동 과정에 넣으면 Redis가 잠깐 안 되는 것만으로 앱이 뜨지 못한다({@code SseConfig} 참고).
 * 그래서 앱은 구독 없이 먼저 뜨고, 구독은 여기서 따로 붙인다. 붙기 전까지의 이벤트는
 * 주기 확인({@code RoundStateWatcher})이 3초 안에 챙기므로 놓치는 것은 없다.
 *
 * <p>5초마다 "지금 구독 중인가"를 보고 아니면 다시 붙인다. 기동 때 실패한 경우와 운영 중 Redis가 재시작된 경우를
 * 같은 방식으로 복구한다. 다시 붙이기 전에는 반드시 {@code stop()}을 부른다. 컨테이너는 시작에 실패해도
 * "시작됨" 표시를 남겨 두어서, 그대로 {@code start()}를 부르면 아무 일도 하지 않는다(로컬에서 확인).
 */
public class RoundEventSubscriptionStarter {

    private static final Logger log = LoggerFactory.getLogger(RoundEventSubscriptionStarter.class);

    private final RedisMessageListenerContainer container;
    /** 같은 실패를 5초마다 경고로 쌓지 않으려는 표시 */
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public RoundEventSubscriptionStarter(RedisMessageListenerContainer container) {
        this.container = container;
    }

    @Scheduled(initialDelayString = "PT1S", fixedDelayString = "PT5S")
    public void ensureListening() {
        if (container.isListening()) {
            return;
        }
        try {
            container.stop();
            container.start();
            failureLogged.set(false);
            log.info("회차 이벤트 구독 시작");
        } catch (RuntimeException exception) {
            if (failureLogged.compareAndSet(false, true)) {
                log.warn("회차 이벤트 구독 실패. 5초마다 다시 시도하고 그동안은 주기 확인이 대신한다: {}", exception.getMessage());
            }
        }
    }
}
