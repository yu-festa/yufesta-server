package com.yufesta.common.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * OAuth 시작 요청의 `redirect` 파라미터(프론트 상대 경로)를 인가 요청의 attributes에 실어 보낸다.
 * 인가 요청은 CookieOAuth2AuthorizationRequestRepository가 쿠키로 보관하므로 이 경로도 함께 브라우저를 오간다
 */
public class OAuth2RedirectRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String REDIRECT_PATH_ATTRIBUTE = "redirect_path";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public OAuth2RedirectRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
    }

    // 인가 요청 리다이렉트 필터가 모든 요청에서 호출하므로, 실제 로그인 시작 요청(위임 결과가 있을 때)에만 경로를 붙인다
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withRedirectPath(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withRedirectPath(delegate.resolve(request, clientRegistrationId), request);
    }

    // 콜백 요청에서 쿠키 저장소가 요청 속성으로 옮겨 둔 경로를 꺼낸다. 없으면 루트
    public static String consumeRedirectUri(HttpServletRequest request) {
        Object redirectPath = request.getAttribute(REDIRECT_PATH_ATTRIBUTE);
        request.removeAttribute(REDIRECT_PATH_ATTRIBUTE);
        return redirectPath instanceof String value ? value : "/";
    }

    // 외부 사이트 이동을 막기 위해 안전한 상대 경로만 싣는다
    private static OAuth2AuthorizationRequest withRedirectPath(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request
    ) {
        if (authorizationRequest == null) {
            return null;
        }
        String redirect = request.getParameter("redirect");
        if (redirect == null || redirect.isBlank()) {
            return authorizationRequest;
        }
        String redirectPath = isSafeRelativeUri(redirect) ? redirect : "/";
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .attributes(attributes -> attributes.put(REDIRECT_PATH_ATTRIBUTE, redirectPath))
                .build();
    }

    private static boolean isSafeRelativeUri(String redirectUri) {
        return redirectUri.startsWith("/")
                && !redirectUri.startsWith("//")
                && !redirectUri.contains("\\");
    }
}
