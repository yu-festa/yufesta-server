package com.yufesta.domain.match.event;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 채널에서 회차 이벤트를 받아 이 태스크의 연결들에 보낸다. 모든 태스크가 하나씩 가진다
 */
@Component
public class RoundEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RoundEventSubscriber.class);

    private final RoundEventBroadcaster broadcaster;

    public RoundEventSubscriber(RoundEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            broadcaster.broadcast(RoundEvent.decode(body));
        } catch (IllegalArgumentException exception) {
            // 형식이 다른 메시지 하나 때문에 구독이 멈추면 안 된다
            log.warn("알 수 없는 회차 이벤트를 버린다: {}", body);
        }
    }
}
