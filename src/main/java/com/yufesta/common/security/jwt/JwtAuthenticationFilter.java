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
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HttpOnly 쿠키의 JWT를 읽어 현재 요청의 인증 정보를 설정
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
    // 요청 쿠키의 JWT로 현재 사용자를 인증합니다.
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

    // 요청 쿠키에서 access_token 값을 찾습니다.
    private Optional<String> extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    // JWT의 uid로 실제 사용자를 조회합니다.
    private Optional<User> findUser(String token) {
        try {
            return userRepository.findById(jwtTokenProvider.getUserId(token));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    // 조회한 사용자를 Spring Security 로그인 정보로 등록합니다.
    private void authenticate(HttpServletRequest request, User user) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getId(),
                null,
                authorities
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
