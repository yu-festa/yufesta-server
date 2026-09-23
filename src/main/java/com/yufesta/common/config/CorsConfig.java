package com.yufesta.common.config;

import com.yufesta.common.security.config.AuthProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 프론트 오리진에서 쿠키를 포함해 API를 호출할 수 있도록 CORS 허용
 */
@Configuration
public class CorsConfig {

    private static final String XSRF_TOKEN_HEADER = "X-XSRF-TOKEN";

    // SecurityConfig의 http.cors()가 이 빈 이름(corsConfigurationSource)을 찾아 사용
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthProperties authProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins(authProperties));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, XSRF_TOKEN_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    // 로컬(5173)·Vercel 프리뷰·운영 도메인을 함께 허용하기 위한 목록. 빈 문자열 바인딩("")은 걸러낸다
    private static List<String> allowedOrigins(AuthProperties authProperties) {
        List<String> configured = authProperties.allowedOrigins() == null ? List.of() : authProperties.allowedOrigins();
        List<String> origins = configured.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        return origins.isEmpty() ? List.of(authProperties.frontendUrl()) : origins;
    }
}
