package com.yufesta.common.security.oauth2;

import org.springframework.util.Assert;

/**
 * 프론트 주소 뒤에 상대 경로를 붙여 로그인 후 이동할 절대 URL을 만든다
 */
final class FrontendUrl {

    private final String baseUrl;

    FrontendUrl(String frontendUrl) {
        Assert.hasText(frontendUrl, "FRONTEND_URL must not be empty");
        this.baseUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    // relativePath는 항상 "/"로 시작하는 서버 내부 경로만 들어온다
    String resolve(String relativePath) {
        return baseUrl + relativePath;
    }
}
