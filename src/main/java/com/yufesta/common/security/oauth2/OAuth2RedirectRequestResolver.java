package com.yufesta.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * OAuth 시작 전에 안전한 원래 이동 경로를 세션에 보관합니다.
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

    @Override
    // 기본 OAuth 요청을 만들고 원래 이동 경로를 저장
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        storeRedirectUri(request);
        return delegate.resolve(request);
    }

    @Override
    // 제공자가 지정된 OAuth 요청을 만들고 원래 이동 경로를 저장
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        storeRedirectUri(request);
        return delegate.resolve(request, clientRegistrationId);
    }

    // 로그인 성공 뒤 저장한 원래 이동 경로를 꺼냄
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
