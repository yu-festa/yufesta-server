package com.yufesta.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP 요청의 메서드, URI, 응답 상태와 처리 시간만 공통 로그로 기록
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final List<String> EXCLUDED_URI_PREFIXES = List.of(
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    // health check와 API 문서 요청은 운영 로그에서 제외
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/actuator/health")
                || uri.equals("/favicon.ico")
                || EXCLUDED_URI_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    // 요청 완료 후 메서드, URI, 상태 코드, 처리 시간만 기록
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info(
                    "HTTP {} {} -> {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis
            );
        }
    }
}
