package com.yufesta.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 소셜 로그인 실패(동의 취소, state 불일치 등) 시 프론트 로그인 화면으로 오류 코드만 붙여 이동
 */
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private static final String LOGIN_PATH = "/login";
    private static final String DEFAULT_ERROR_CODE = "login_failed";

    private final FrontendUrl frontendUrl;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public OAuth2LoginFailureHandler(String frontendUrl) {
        this.frontendUrl = new FrontendUrl(frontendUrl);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        String errorCode = resolveErrorCode(exception);
        log.info("소셜 로그인 실패: {}", errorCode);

        HttpSessionCleaner.invalidate(request);
        redirectStrategy.sendRedirect(
                request,
                response,
                frontendUrl.resolve(LOGIN_PATH + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8))
        );
    }

    // 제공자가 준 표준 오류 코드(access_denied 등)만 노출하고 나머지는 일반 코드로 감춘다
    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            return oauth2Exception.getError().getErrorCode();
        }
        return DEFAULT_ERROR_CODE;
    }
}
