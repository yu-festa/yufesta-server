package com.yufesta.domain.auth.controller;

import com.yufesta.common.security.jwt.AuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 쿠키를 정리하는 API
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthCookieService authCookieService;

    public AuthController(AuthCookieService authCookieService) {
        this.authCookieService = authCookieService;
    }

    /**
     * 브라우저가 쓰기 요청 전에 CSRF 쿠키를 발급받는 엔드포인트
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // 쓰기 요청에 사용할 XSRF-TOKEN 쿠키를 발급
    public void issueCsrfToken(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // JWT 쿠키를 삭제해 현재 브라우저를 로그아웃
    public void logout(HttpServletResponse response) {
        authCookieService.deleteAccessToken(response);
    }
}
