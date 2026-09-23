package com.yufesta.common.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class OAuth2RedirectRequestResolverTest {

    private static final String KEY = OAuth2RedirectRequestResolver.REDIRECT_PATH_ATTRIBUTE;

    private final OAuth2RedirectRequestResolver resolver = new OAuth2RedirectRequestResolver(
            new InMemoryClientRegistrationRepository(kakaoRegistration())
    );

    @Test
    void 로그인_시작_요청의_안전한_상대_경로를_인가_요청_속성에_싣는다() {
        OAuth2AuthorizationRequest request = resolver.resolve(loginStartRequest("/match/apply?tab=1"));

        assertThat(request).isNotNull();
        assertThat(request.<String>getAttribute(KEY)).isEqualTo("/match/apply?tab=1");
        assertThat(request.<String>getAttribute("registration_id")).isEqualTo("kakao");
    }

    @Test
    void 외부_주소나_백슬래시가_있는_경로는_루트로_바꾼다() {
        for (String unsafe : new String[] {"//evil.com", "https://evil.com", "/a\\b", "evil.com"}) {
            OAuth2AuthorizationRequest request = resolver.resolve(loginStartRequest(unsafe));

            assertThat(request.<String>getAttribute(KEY)).as("redirect=%s", unsafe).isEqualTo("/");
        }
    }

    @Test
    void redirect가_없으면_속성을_넣지_않고_세션도_만들지_않는다() {
        MockHttpServletRequest start = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");

        OAuth2AuthorizationRequest request = resolver.resolve(start);

        assertThat(request).isNotNull();
        assertThat(request.<String>getAttribute(KEY)).isNull();
        assertThat(start.getSession(false)).isNull();
    }

    @Test
    void 로그인_시작이_아닌_요청은_null이다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetable");
        request.setParameter("redirect", "/match/apply");

        assertThat(resolver.resolve(request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 요청_속성의_경로를_꺼내면_제거되고_없으면_루트다() {
        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setAttribute(KEY, "/match/apply");

        assertThat(OAuth2RedirectRequestResolver.consumeRedirectUri(callback)).isEqualTo("/match/apply");
        assertThat(OAuth2RedirectRequestResolver.consumeRedirectUri(callback)).isEqualTo("/");
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
