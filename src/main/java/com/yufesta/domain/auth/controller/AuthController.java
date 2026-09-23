package com.yufesta.domain.auth.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.common.security.jwt.AuthCookieService;
import com.yufesta.domain.auth.controller.api.AuthApi;
import com.yufesta.domain.auth.dto.response.AuthMeResponse;
import com.yufesta.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. 명세는 AuthApi
 */
@RestController
public class AuthController implements AuthApi {

    private final AuthCookieService authCookieService;
    private final AuthService authService;

    public AuthController(AuthCookieService authCookieService, AuthService authService) {
        this.authCookieService = authCookieService;
        this.authService = authService;
    }

    @Override
    public void issueCsrfToken(CsrfToken csrfToken) {
        // 토큰을 읽어야 CookieCsrfTokenRepository가 XSRF-TOKEN 쿠키를 응답에 싣는다
        csrfToken.getToken();
    }

    @Override
    public void logout(HttpServletResponse response) {
        authCookieService.deleteAccessToken(response);
    }

    @Override
    public ApiResponse<AuthMeResponse> getMe(Long userId) {
        return ApiResponse.success(authService.getMe(userId));
    }
}
