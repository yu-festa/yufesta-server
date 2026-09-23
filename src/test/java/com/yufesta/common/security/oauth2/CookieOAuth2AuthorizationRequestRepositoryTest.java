package com.yufesta.common.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import tools.jackson.databind.ObjectMapper;

class CookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long";
    private static final String NAME = CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME;

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository(new ObjectMapper(), SECRET, false);

    @Test
    void 저장하면_서명된_HttpOnly_쿠키가_5분_만료로_내려간다() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(request("state-1"), new MockHttpServletRequest(), response);

        String setCookie = setCookie(response);
        assertThat(setCookie).startsWith(NAME + "=")
                .contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").contains("Max-Age=300");
        assertThat(cookieValue(response)).contains(".");
    }

    @Test
    void 쿠키를_들고_온_요청에서_같은_인가_요청을_복원한다() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(request("state-1"), new MockHttpServletRequest(), saved);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(requestWithCookie(cookieValue(saved)));

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-1");
        assertThat(loaded.getRedirectUri()).isEqualTo("https://api.yufesta.com/login/oauth2/code/kakao");
        assertThat(loaded.getScopes()).containsExactly("openid");
        assertThat(loaded.getAdditionalParameters()).containsEntry("nonce", "hashed-nonce");
        assertThat(loaded.getAttributes())
                .containsEntry("registration_id", "kakao")
                .containsEntry("nonce", "raw-nonce")
                .containsEntry("code_verifier", "pkce-verifier")
                .containsEntry(OAuth2RedirectRequestResolver.REDIRECT_PATH_ATTRIBUTE, "/match/apply");
        assertThat(loaded.getAuthorizationRequestUri()).startsWith("https://kauth.kakao.com/oauth/authorize?");
    }

    @Test
    void 제거하면_쿠키가_만료되고_이동_경로가_요청_속성으로_남는다() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(request("state-1"), new MockHttpServletRequest(), saved);
        MockHttpServletRequest callback = requestWithCookie(cookieValue(saved));
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(callback, response);

        assertThat(removed).isNotNull();
        assertThat(setCookie(response)).startsWith(NAME + "=;").contains("Max-Age=0");
        assertThat(OAuth2RedirectRequestResolver.consumeRedirectUri(callback)).isEqualTo("/match/apply");
        assertThat(OAuth2RedirectRequestResolver.consumeRedirectUri(callback)).isEqualTo("/");
    }

    @Test
    void 서명이_다르거나_손상된_쿠키는_null이다() {
        MockHttpServletResponse saved = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(request("state-1"), new MockHttpServletRequest(), saved);
        String value = cookieValue(saved);
        String payload = value.substring(0, value.indexOf('.'));
        String signature = value.substring(value.indexOf('.') + 1);

        for (String tampered : List.of(
                payload + "x." + signature,
                payload + "." + signature.substring(1) + "A",
                "garbage",
                payload
        )) {
            assertThat(repository.loadAuthorizationRequest(requestWithCookie(tampered))).as(tampered).isNull();
        }
    }

    @Test
    void 쿠키가_없으면_null이고_null을_저장하면_쿠키를_만료시킨다() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();

        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(null, new MockHttpServletRequest(), response);

        assertThat(setCookie(response)).contains("Max-Age=0");
    }

    private static OAuth2AuthorizationRequest request(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .clientId("client")
                .redirectUri("https://api.yufesta.com/login/oauth2/code/kakao")
                .scopes(Set.of("openid"))
                .state(state)
                .additionalParameters(Map.of("nonce", "hashed-nonce"))
                // PKCE code_verifier·OIDC nonce처럼 콜백 검증에 필요한 값이 attributes에 있다. 하나라도 빠지면 로그인이 실패한다
                .attributes(Map.of(
                        "registration_id", "kakao",
                        "nonce", "raw-nonce",
                        "code_verifier", "pkce-verifier",
                        OAuth2RedirectRequestResolver.REDIRECT_PATH_ATTRIBUTE, "/match/apply"))
                .build();
    }

    private static MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
        request.setCookies(new Cookie(NAME, value));
        return request;
    }

    private static String setCookie(MockHttpServletResponse response) {
        return response.getHeader(HttpHeaders.SET_COOKIE);
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String header = setCookie(response);
        return header.substring(NAME.length() + 1, header.indexOf(';'));
    }
}
