package com.yufesta.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * OAuth 시작 전에 안전한 원래 이동 경로(프론트 상대 경로)를 세션에 보관
 * state 자체의 검증은 Spring Security의 세션 기반 저장소가 담당
 */
public class OAuth2RedirectRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String REDIRECT_URI_SESSION_ATTRIBUTE = "OAUTH2_REDIRECT_URI";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public OAuth2RedirectRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
    }

    // 인가 요청 리다이렉트 필터가 모든 요청에서 호출하므로, 실제 로그인 시작 요청일 때만 경로를 저장
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        if (authorizationRequest != null) {
            storeRedirectUri(request);
        }
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        if (authorizationRequest != null) {
            storeRedirectUri(request);
        }
        return authorizationRequest;
    }

    // 로그인 성공 뒤 저장한 원래 이동 경로(상대 경로)를 꺼냄. 없으면 루트
    public static String consumeRedirectUri(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "/";
        }

        Object redirectUri = session.getAttribute(REDIRECT_URI_SESSION_ATTRIBUTE);
        session.removeAttribute(REDIRECT_URI_SESSION_ATTRIBUTE);
        return redirectUri instanceof String value ? value : "/";
    }

    // 외부 사이트 이동을 막기 위해 안전한 상대 경로만 세션에 저장
    private void storeRedirectUri(HttpServletRequest request) {
        String redirectUri = request.getParameter("redirect");
        if (redirectUri == null || redirectUri.isBlank()) {
            return;
        }

        request.getSession().setAttribute(
                REDIRECT_URI_SESSION_ATTRIBUTE,
                isSafeRelativeUri(redirectUri) ? redirectUri : "/"
        );
    }

    // 서버 내부의 상대 경로인지 확인
    private boolean isSafeRelativeUri(String redirectUri) {
        return redirectUri.startsWith("/")
                && !redirectUri.startsWith("//")
                && !redirectUri.contains("\\");
    }
}
