package com.yufesta.domain.match.event;

import com.yufesta.common.sse.SseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회차 이벤트를 Redis 채널에 발행한다. 태스크가 여러 개여도 모두가 같은 채널을 구독하므로,
 * 어느 태스크에서 회차가 바뀌든 모든 태스크의 연결에 닿는다.
 *
 * <pre>
 *   태스크 A: 1회차 발표 → 커밋 → PUBLISH yufesta:events:round "PUBLISHED:1"
 *                                        │
 *                    ┌───────────────────┴───────────────────┐
 *   태스크 A 구독자: 자기 연결 500개에 전송     태스크 B 구독자: 자기 연결 500개에 전송
 * </pre>
 * 발행한 태스크도 구독자로서 자기 메시지를 받는다. 그래서 여기서는 직접 보내지 않는다(경로를 하나로).
 *
 * <h2>커밋 뒤에 발행한다</h2>
 * 커밋 전에 알리면 브라우저가 조회했을 때 아직 옛 상태가 나온다({@code PublicCacheEvictor}와 같은 이유).
 *
 * <h2>Redis가 안 될 때</h2>
 * 발행에 실패하면 이 태스크의 연결에는 직접 보낸다. 다른 태스크의 연결은 주기 확인이 3초 안에 챙긴다.
 */
@Component
public class RedisRoundEventPublisher implements RoundEventPublisher {

    public static final String CHANNEL = "yufesta:events:round";
    private static final Logger log = LoggerFactory.getLogger(RedisRoundEventPublisher.class);

    private final StringRedisTemplate redis;
    private final RoundEventBroadcaster broadcaster;
    private final SseProperties properties;

    public RedisRoundEventPublisher(
            StringRedisTemplate redis,
            RoundEventBroadcaster broadcaster,
            SseProperties properties
    ) {
        this.redis = redis;
        this.broadcaster = broadcaster;
        this.properties = properties;
    }

    @Override
    public void publish(RoundEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(event);
            }
        });
    }

    private void send(RoundEvent event) {
        if (!properties.pubsubEnabled()) {
            broadcaster.broadcast(event);
            return;
        }
        try {
            redis.convertAndSend(CHANNEL, event.encode());
        } catch (RuntimeException exception) {
            log.warn("회차 이벤트 발행 실패({}). 이 태스크의 연결에만 직접 보낸다: {}", event.encode(), exception.getMessage());
            broadcaster.broadcast(event);
        }
    }
}
