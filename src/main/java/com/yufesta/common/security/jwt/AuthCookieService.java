package com.yufesta.common.security.jwt;

import com.yufesta.common.security.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

/**
 * JWT를 HttpOnly 쿠키로 전달하고 제거
 */
public class AuthCookieService {

    private final AuthProperties.Cookie properties;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthCookieService(AuthProperties.Cookie properties, JwtTokenProvider jwtTokenProvider) {
        this.properties = properties;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 로그인 성공 후 JWT 쿠키를 응답에 추가
    public void addAccessToken(HttpServletResponse response, String accessToken) {
        addCookie(response, accessToken, jwtTokenProvider.getAccessTokenMaxAgeSeconds());
    }

    // 로그아웃 시 JWT 쿠키를 만료
    public void deleteAccessToken(HttpServletResponse response) {
        addCookie(response, "", 0);
    }

    // 공통 쿠키 옵션을 적용해 Set-Cookie 헤더를 추가
    private void addCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds);

        if (StringUtils.hasText(properties.domain())) {
            cookie.domain(properties.domain());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
