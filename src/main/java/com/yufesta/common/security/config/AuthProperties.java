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

    /**
     * 쿠키 옵션. domain의 선행 '.'은 뗀다. 브라우저는 RFC 6265에 따라 무시하는 문자지만
     * Tomcat의 Rfc6265CookieProcessor는 거부하므로, response.addCookie로 나가는 XSRF-TOKEN이 500이 된다
     */
    public record Cookie(
            String name,
            boolean secure,
            String domain
    ) {
        public Cookie {
            if (domain != null && domain.startsWith(".")) {
                domain = domain.substring(1);
            }
        }
    }
}
