package com.yufesta.common.security.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 토큰과 쿠키에 사용하는 환경 설정
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        Jwt jwt,
        Cookie cookie
) {

    public record Jwt(
            String secret,
            Duration accessTokenValidity
    ) {
    }

    public record Cookie(
            String name,
            boolean secure,
            String domain
    ) {
    }
}
