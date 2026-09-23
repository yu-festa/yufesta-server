package com.yufesta.domain.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.auth.service.AuthService;
import com.yufesta.support.ControllerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 운영처럼 쿠키 domain을 지정하면 XSRF-TOKEN 쿠키에도 붙는지 확인. 프론트(yufesta.com)가 api.yufesta.com의 CSRF 쿠키를 읽으려면 필요하다
 */
@WebMvcTest(controllers = AuthController.class)
@TestPropertySource(properties = "app.auth.cookie.domain=.yufesta.com")
class CsrfCookieDomainTest extends ControllerTestSupport {

    @MockitoBean
    private AuthService authService;

    @Test
    void CSRF_쿠키에_설정한_domain이_붙는다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().domain("XSRF-TOKEN", ".yufesta.com"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
