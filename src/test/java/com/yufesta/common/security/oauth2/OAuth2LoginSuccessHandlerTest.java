package com.yufesta.common.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.security.jwt.AuthCookieService;
import com.yufesta.common.security.jwt.JwtTokenProvider;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.service.UserLoginService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private UserLoginService userLoginService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthCookieService authCookieService;

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(
                userLoginService,
                new OAuth2ProviderUserIdExtractor(),
                jwtTokenProvider,
                authCookieService,
                "http://localhost:3000/"
        );
    }

    @Test
    void 로그인_처리_후_쿠키를_발급하고_프론트의_원래_화면으로_보낸다() throws Exception {
        User user = User.builder().provider(OAuthProvider.KAKAO).providerUserId("12345").role(UserRole.USER).loginAt(LocalDateTime.of(2026, 10, 8, 12, 0)).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userLoginService.login(OAuthProvider.KAKAO, "12345")).thenReturn(user);
        when(jwtTokenProvider.createAccessToken(7L)).thenReturn("jwt");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(OAuth2RedirectRequestResolver.REDIRECT_URI_SESSION_ATTRIBUTE, "/match/apply");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, kakaoAuthentication(12345L));

        verify(authCookieService).addAccessToken(response, "jwt");
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/match/apply");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void 저장된_경로가_없으면_프론트_루트로_보낸다() throws Exception {
        User user = User.builder().provider(OAuthProvider.KAKAO).providerUserId("12345").role(UserRole.USER).loginAt(LocalDateTime.of(2026, 10, 8, 12, 0)).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userLoginService.login(OAuthProvider.KAKAO, "12345")).thenReturn(user);
        when(jwtTokenProvider.createAccessToken(7L)).thenReturn("jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, kakaoAuthentication(12345L));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/");
    }

    private static OAuth2AuthenticationToken kakaoAuthentication(Long kakaoId) {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("id", kakaoId),
                "id"
        );
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }
}
