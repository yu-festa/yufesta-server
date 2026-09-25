package com.yufesta.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP 요청 처리 결과를 공통 로그로 남기고, 로그 한 줄마다 요청 ID와 사용자 ID를 MDC로 붙인다.
 * <p>요청 ID는 클라이언트가 보낸 {@code X-Request-Id}를 쓰고 없으면 만들며, 응답 헤더로 돌려준다.
 * 부하 테스트·장애 분석에서 한 요청이 남긴 여러 줄을 묶어 보기 위한 것이다.
 * 사용자 ID는 운영자 API 호출자를 접근 로그에서 식별하기 위해 남긴다(NFR-SC-05). 개인정보는 넣지 않는다
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String REQUEST_ID_KEY = "requestId";
    static final String USER_ID_KEY = "userId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    // 외부에서 온 값을 그대로 로그에 넣으므로 형식을 제한한다(로그 위조 방지)
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private static final List<String> EXCLUDED_URI_PREFIXES = List.of(
            "/swagger-ui",
            "/v3/api-docs"
    );

    @Override
    // health check와 Swagger 요청은 로그에서 제외
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/actuator/health")
                || uri.equals("/favicon.ico")
                || EXCLUDED_URI_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    @Override
    // 요청 처리 후 상태 코드와 처리 시간을 기록
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        // 이 필터는 보안 필터 체인 뒤에서 돌아 인증이 이미 끝나 있다. 체인 안에서 인증되는 경우를 위해 finally에서 한 번 더 본다
        putUserIdIfPresent();

        try {
            filterChain.doFilter(request, response);
        } finally {
            putUserIdIfPresent();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info(
                    "HTTP {} {} -> {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis
            );
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(USER_ID_KEY);
        }
    }

    // 프론트·로드밸런서가 보낸 요청 ID를 이어받아 같은 요청을 양쪽 로그에서 찾을 수 있게 한다
    private static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(header) && SAFE_REQUEST_ID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static void putUserIdIfPresent() {
        if (MDC.get(USER_ID_KEY) != null) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            MDC.put(USER_ID_KEY, String.valueOf(userId));
        }
    }
}
