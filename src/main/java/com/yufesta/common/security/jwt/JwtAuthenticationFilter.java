package com.yufesta.common.security.jwt;

import com.yufesta.domain.user.enums.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HttpOnly 쿠키의 JWT를 읽어 현재 요청의 인증 정보를 설정
 *
 * <p>흐름: 쿠키에서 토큰 → 서명·만료 검증으로 회원 id → 그 회원의 역할 → SecurityContext 등록.
 * 토큰에는 회원 id만 있어 역할은 따로 알아내야 한다. 일반 요청은 {@link UserRoleCache}가 기억해 둔 값을 쓰고,
 * 운영자 경로는 매번 DB에서 읽는다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    /** SecurityConfig에서 운영자 역할을 요구하는 경로. 여기서는 역할이 방금 바뀌었어도 바로 반영돼야 한다 */
    private static final String[] OPERATOR_PATHS = {"/api/v1/admin", "/actuator"};

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRoleCache userRoleCache;
    private final String cookieName;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserRoleCache userRoleCache,
            String cookieName
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRoleCache = userRoleCache;
        this.cookieName = cookieName;
    }

    @Override
    // 요청 쿠키의 JWT로 현재 사용자를 인증
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        extractToken(request)
                .flatMap(this::findUserId)
                .ifPresent(userId -> findRole(request, userId)
                        .ifPresent(role -> authenticate(request, userId, role)));
        filterChain.doFilter(request, response);
    }

    // 요청 쿠키에서 access_token 값을 찾음
    private Optional<String> extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    // 토큰이 위조·만료됐으면 익명으로 계속 진행
    private Optional<Long> findUserId(String token) {
        try {
            return Optional.of(jwtTokenProvider.getUserId(token));
        } catch (JwtException exception) {
            log.debug("유효하지 않은 access_token 쿠키: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    // 회원이 없으면(탈퇴·파기) 익명으로 진행. DB 예외는 잡지 않고 전파해 500으로 드러낸다
    private Optional<UserRole> findRole(HttpServletRequest request, Long userId) {
        return isOperatorPath(request) ? userRoleCache.loadFresh(userId) : userRoleCache.find(userId);
    }

    private static boolean isOperatorPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String operatorPath : OPERATOR_PATHS) {
            if (path.equals(operatorPath) || path.startsWith(operatorPath + "/")) {
                return true;
            }
        }
        return false;
    }

    // 회원 id와 역할을 Spring Security 로그인 정보로 등록
    private void authenticate(HttpServletRequest request, Long userId, UserRole role) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication = LoginAuthentication.of(userId, role);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
