package com.yufesta.domain.match.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yufesta.common.sse.SseProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 발행 시점(커밋 뒤)과 Redis 장애 시 동작을 고정한다
 */
@ExtendWith(MockitoExtension.class)
class RedisRoundEventPublisherTest {

    private static final RoundEvent PUBLISHED = new RoundEvent(RoundEventType.PUBLISHED, 1);

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private RoundEventBroadcaster broadcaster;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 채널에_발행하고_직접_보내지는_않는다() {
        publisher(true).publish(PUBLISHED);

        verify(redis).convertAndSend(RedisRoundEventPublisher.CHANNEL, "PUBLISHED:1");
        // 자기 메시지도 구독자로 받으므로 여기서 또 보내면 두 번 나간다
        verifyNoInteractions(broadcaster);
    }

    @Test
    void 트랜잭션_안에서는_커밋된_뒤에_발행한다() {
        TransactionSynchronizationManager.initSynchronization();

        publisher(true).publish(PUBLISHED);

        // 커밋 전에 알리면 브라우저가 조회했을 때 아직 옛 상태가 나온다
        verify(redis, never()).convertAndSend(anyString(), any(Object.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(redis).convertAndSend(RedisRoundEventPublisher.CHANNEL, "PUBLISHED:1");
    }

    @Test
    void 롤백되면_발행하지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        publisher(true).publish(PUBLISHED);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(redis, broadcaster);
    }

    @Test
    void Redis에_발행하지_못하면_이_태스크의_연결에는_직접_보낸다() {
        when(redis.convertAndSend(anyString(), any(Object.class)))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));

        publisher(true).publish(PUBLISHED);   // 예외가 새면 발표 요청이 실패한다

        verify(broadcaster).broadcast(PUBLISHED);
    }

    @Test
    void Pub_Sub를_끄면_Redis를_거치지_않고_직접_보낸다() {
        publisher(false).publish(PUBLISHED);

        verify(broadcaster).broadcast(PUBLISHED);
        verifyNoInteractions(redis);
    }

    private RedisRoundEventPublisher publisher(boolean pubsubEnabled) {
        SseProperties properties = new SseProperties(100, Duration.ofSeconds(25), Duration.ofMinutes(30), pubsubEnabled);
        return new RedisRoundEventPublisher(redis, broadcaster, properties);
    }
}
