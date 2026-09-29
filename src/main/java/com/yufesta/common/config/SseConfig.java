package com.yufesta.common.config;

import com.yufesta.common.sse.SseConnectionRegistry;
import com.yufesta.common.sse.SseProperties;
import com.yufesta.domain.match.event.RedisRoundEventPublisher;
import com.yufesta.domain.match.event.RoundEventSubscriber;
import com.yufesta.domain.match.event.RoundEventSubscriptionStarter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * SSE 연결 관리와 Redis 구독을 구성한다
 */
@Configuration
@EnableConfigurationProperties(SseProperties.class)
public class SseConfig {

    /**
     * 전송 전용 스레드 하나. 이벤트를 순서대로 보내고(발표 → 다음 회차 접수),
     * 회차를 바꾼 스레드가 전송을 기다리지 않게 한다
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService sseBroadcastExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-broadcast");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean
    public SseConnectionRegistry sseConnectionRegistry(
            SseProperties properties,
            ExecutorService sseBroadcastExecutor,
            ObjectProvider<MeterRegistry> meterRegistry
    ) {
        SseConnectionRegistry registry = new SseConnectionRegistry(properties, sseBroadcastExecutor);
        // 현재 연결 수. /actuator/metrics/sse.connections 로 본다(NFR-AV-05)
        meterRegistry.ifAvailable(meters -> Gauge
                .builder("sse.connections", registry, SseConnectionRegistry::connectionCount)
                .description("이 태스크에 열려 있는 SSE 연결 수")
                .register(meters));
        return registry;
    }

    /**
     * 회차 이벤트 채널 구독. <b>스스로 시작하지 않게 둔다</b>({@code autoStartup=false}).
     * <p>이 컨테이너는 시작할 때 Redis에 붙지 못하면 예외를 던지고, 그 예외는 앱 기동을 통째로 실패시킨다
     * (로컬에서 Redis를 끄고 확인). 운영에서는 기동 직후 Redis 첫 연결이 자주 실패했으므로(캐시 예열이 2~3회 만에 붙었다)
     * 그대로 두면 배포할 때 태스크가 뜨지 못한다. 캐시와 마찬가지로 실시간 알림도 기동을 막으면 안 된다.
     * 시작은 {@link RoundEventSubscriptionStarter}가 기동 뒤에 하고, 실패하면 다시 시도한다.
     */
    @Bean
    @ConditionalOnProperty(name = "app.sse.pubsub-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer roundEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            RoundEventSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisRoundEventPublisher.CHANNEL));
        container.setAutoStartup(false);
        return container;
    }

    @Bean
    @ConditionalOnProperty(name = "app.sse.pubsub-enabled", havingValue = "true", matchIfMissing = true)
    public RoundEventSubscriptionStarter roundEventSubscriptionStarter(
            RedisMessageListenerContainer roundEventListenerContainer
    ) {
        return new RoundEventSubscriptionStarter(roundEventListenerContainer);
    }
}
