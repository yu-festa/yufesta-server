package com.yufesta.common.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2LoginFailureHandlerTest {

    private final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler("http://localhost:3000");

    @Test
    void 제공자_오류_코드만_붙여_프론트_로그인_화면으로_보내고_세션을_지운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("anything", "x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied", "사용자가 동의를 취소", null))
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=access_denied");
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void OAuth_오류가_아니면_일반_코드로_감춘다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new BadCredentialsException("내부 상세"));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=login_failed");
    }
}
