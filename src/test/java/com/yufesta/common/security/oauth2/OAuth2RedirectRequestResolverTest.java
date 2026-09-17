package com.yufesta.common.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class OAuth2RedirectRequestResolverTest {

    private static final String SESSION_KEY = OAuth2RedirectRequestResolver.REDIRECT_URI_SESSION_ATTRIBUTE;

    private final OAuth2RedirectRequestResolver resolver = new OAuth2RedirectRequestResolver(
            new InMemoryClientRegistrationRepository(kakaoRegistration())
    );

    @Test
    void 로그인_시작_요청의_안전한_상대_경로를_세션에_저장한다() {
        MockHttpServletRequest request = loginStartRequest("/match/apply?tab=1");

        assertThat(resolver.resolve(request)).isNotNull();
        assertThat(request.getSession(false).getAttribute(SESSION_KEY)).isEqualTo("/match/apply?tab=1");
    }

    @Test
    void 외부_주소나_백슬래시가_있는_경로는_루트로_바꾼다() {
        for (String unsafe : new String[] {"//evil.com", "https://evil.com", "/a\\b", "evil.com"}) {
            MockHttpServletRequest request = loginStartRequest(unsafe);

            resolver.resolve(request);

            assertThat(request.getSession(false).getAttribute(SESSION_KEY))
                    .as("redirect=%s", unsafe)
                    .isEqualTo("/");
        }
    }

    @Test
    void 로그인_시작이_아닌_요청은_redirect가_있어도_세션을_만들지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetable");
        request.setParameter("redirect", "/match/apply");

        assertThat(resolver.resolve(request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 저장된_경로를_꺼내면_세션에서_제거되고_없으면_루트다() {
        MockHttpServletRequest request = loginStartRequest("/match/apply");
        resolver.resolve(request);

        assertThat(OAuth2RedirectRequestResolver.consumeRedirectUri(request)).isEqualTo("/match/apply");
        assertThat(request.getSession(false).getAttribute(SESSION_KEY)).isNull();
        assertThat(OAuth2RedirectRequestResolver.consumeRedirectUri(new MockHttpServletRequest())).isEqualTo("/");
    }

    private MockHttpServletRequest loginStartRequest(String redirect) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
        request.setParameter("redirect", redirect);
        return request;
    }

    private static ClientRegistration kakaoRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }
}
