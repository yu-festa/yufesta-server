package com.yufesta.common.security.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 토큰, 쿠키, 프론트 주소에 사용하는 환경 설정.
 * frontendUrl은 로그인 후 리다이렉트 기준, allowedOrigins는 CORS 허용 목록(비어 있으면 frontendUrl 하나)
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String frontendUrl,
        List<String> allowedOrigins,
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
