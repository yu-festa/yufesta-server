package com.yufesta.domain.auth.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.auth.dto.response.AuthMeResponse;
import com.yufesta.domain.auth.service.AuthService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest extends ControllerTestSupport {

    @MockitoBean
    private AuthService authService;

    @Test
    @WithMockLoginUser(id = 7L, role = UserRole.STAFF)
    void 로그인_사용자의_역할을_반환한다() throws Exception {
        when(authService.getMe(7L)).thenReturn(AuthMeResponse.of(UserRole.STAFF));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.role").value("STAFF"))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    void 비로그인이면_principal이_null로_넘어가_401_JSON이_된다() throws Exception {
        when(authService.getMe(null)).thenThrow(new CustomException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void CSRF_발급은_204이고_XSRF_TOKEN_쿠키를_내려준다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void 로그아웃을_GET으로_부르면_500이_아니라_405_JSON이다() throws Exception {
        mockMvc.perform(get("/api/v1/auth/logout"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 로그아웃은_204이고_access_token_쿠키를_만료시킨다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("access_token", ""))
                .andExpect(cookie().maxAge("access_token", 0));
    }
}
