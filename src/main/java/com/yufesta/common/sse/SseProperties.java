package com.yufesta.common.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 설정(app.sse.*). 값이 없으면 아래 기본값을 쓴다
 *
 * @param maxConnections  태스크 하나가 받는 연결 수 상한. 넘으면 503을 주고 프론트는 폴링으로 동작한다
 * @param keepAlive       빈 메시지를 보내는 간격. ALB가 60초 동안 조용한 연결을 끊으므로 그보다 짧아야 한다
 * @param connectionTtl   연결 하나의 최대 수명. 끝나면 브라우저가 자동으로 다시 연결한다
 * @param pubsubEnabled   Redis Pub/Sub로 다른 태스크에 전달할지. 끄면 자기 태스크의 연결에만 보낸다(테스트·Redis 없는 환경)
 */
@ConfigurationProperties(prefix = "app.sse")
public record SseProperties(
        Integer maxConnections,
        Duration keepAlive,
        Duration connectionTtl,
        Boolean pubsubEnabled
) {

    public SseProperties {
        maxConnections = maxConnections == null ? 3_000 : maxConnections;
        keepAlive = keepAlive == null ? Duration.ofSeconds(25) : keepAlive;
        connectionTtl = connectionTtl == null ? Duration.ofMinutes(30) : connectionTtl;
        pubsubEnabled = pubsubEnabled == null || pubsubEnabled;
    }
}
