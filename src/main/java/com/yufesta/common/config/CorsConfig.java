package com.yufesta.common.config;

import com.yufesta.common.security.config.AuthProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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
        configuration.setAllowedOrigins(List.of(authProperties.frontendUrl()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, XSRF_TOKEN_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
