package com.yufesta.common.security.jwt;

import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.repository.UserRepository;
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
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final String cookieName;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            String cookieName
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
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
                .flatMap(this::findUser)
                .ifPresent(user -> authenticate(request, user));

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

    // 토큰이 위조·만료됐으면 익명으로 계속 진행. DB 예외는 잡지 않고 전파해 500으로 드러낸다
    private Optional<User> findUser(String token) {
        Long userId;
        try {
            userId = jwtTokenProvider.getUserId(token);
        } catch (JwtException exception) {
            log.debug("유효하지 않은 access_token 쿠키: {}", exception.getMessage());
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }

    // 조회한 사용자를 Spring Security 로그인 정보로 등록
    private void authenticate(HttpServletRequest request, User user) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication = LoginAuthentication.of(user.getId(), user.getRole());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
