package com.yufesta.domain.auth.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.auth.dto.response.AuthMeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 인증 API 명세. 로그인 시작은 브라우저 페이지 이동 GET /oauth2/authorization/{kakao|google}?redirect=/경로
 */
@Tag(name = "Auth", description = "인증 (FR-AUTH)")
@RequestMapping("/api/v1/auth")
public interface AuthApi {

    @Operation(
            summary = "CSRF 토큰 발급",
            description = "쓰기 요청 전에 최초 1회 호출한다. XSRF-TOKEN 쿠키를 발급하며 이후 쓰기 요청은 "
                    + "그 값을 X-XSRF-TOKEN 헤더로 보낸다. 로그인 불필요."
    )
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void issueCsrfToken(@Parameter(hidden = true) CsrfToken csrfToken);

    @Operation(
            summary = "로그아웃",
            description = "access_token 쿠키를 삭제한다. 로그인 불필요, X-XSRF-TOKEN 헤더 필요. FR-AUTH-09"
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Parameter(hidden = true) HttpServletResponse response);

    @Operation(
            summary = "내 로그인 상태",
            description = "로그인 사용자의 역할을 반환한다. 비로그인이면 401. 프론트 로그인 게이트용. 로그인 필요."
    )
    @GetMapping("/me")
    ApiResponse<AuthMeResponse> getMe(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
