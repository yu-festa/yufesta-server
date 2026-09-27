package com.yufesta.common.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 응답 캐시 설정(app.cache).
 * <p>{@code enabled}가 false면 캐시를 전혀 쓰지 않는다(장애 시 즉시 되돌리는 스위치).
 * {@code version}은 키 접두사에 들어가 응답 형태가 바뀌는 배포에서 옛 캐시를 한 번에 버리는 용도다
 */
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        boolean enabled,
        String version,
        Duration staleWindow,
        Duration lockTimeout
) {

    public CacheProperties {
        // 만료된 값을 얼마나 더 쓸 수 있는지(stale-while-revalidate). 스탬피드 방지의 핵심 여유분
        staleWindow = staleWindow == null ? Duration.ofSeconds(30) : staleWindow;
        // 재계산 권한을 쥔 쪽이 죽어도 이 시간 뒤에는 다른 요청이 다시 시도할 수 있다
        lockTimeout = lockTimeout == null ? Duration.ofSeconds(3) : lockTimeout;
        version = version == null || version.isBlank() ? "v1" : version;
    }
}
