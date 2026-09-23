package com.yufesta.common.security.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.security.oauth2.CookieOAuth2AuthorizationRequestRepository;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.service.MatchRoundBatchService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.TestEndpointController;
import com.yufesta.support.WithMockLoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "server.forward-headers-strategy=framework",
        "app.auth.allowed-origins=http://localhost:3000,https://yufesta-web.vercel.app"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEndpointController.class)
class SecurityConfigTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    // 발표 경로는 실제 AdminMatchController가 처리하므로 서비스만 mock으로 대체한다
    @MockitoBean
    private MatchRoundBatchService matchRoundBatchService;

    @Test
    void 공개_읽기_API는_비로그인으로_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void 비로그인_쓰기는_401_JSON이며_리다이렉트와_세션이_없다() throws Exception {
        mockMvc.perform(post("/api/v1/ping").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    @WithMockLoginUser
    void 로그인_사용자의_쓰기는_허용된다() throws Exception {
        mockMvc.perform(post("/api/v1/ping").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockLoginUser
    void CSRF_토큰_없는_쓰기는_403_JSON이다() throws Exception {
        mockMvc.perform(post("/api/v1/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void USER는_운영자_API에_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_운영자_API에_접근하지만_발표와_설정은_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ping"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/match/rounds/1/publish").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/settings/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockLoginUser(role = UserRole.OWNER)
    void OWNER는_발표와_설정에_접근한다() throws Exception {
        org.mockito.Mockito.when(matchRoundBatchService.publish(1L))
                .thenReturn(AdminMatchRoundResponse.builder().id(1L).seq(1).status(RoundStatus.PUBLISHED).build());

        mockMvc.perform(post("/api/v1/admin/match/rounds/1/publish").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/settings/ping"))
                .andExpect(status().isOk());
    }

    @Test
    void 응원_메시지_작성과_삭제는_비로그인으로_허용된다() throws Exception {
        mockMvc.perform(post("/api/v1/cheers").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/cheers/1").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void health_check는_비로그인으로_허용된다() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockLoginUser
    void 규칙에_없는_경로는_로그인해도_거부된다() throws Exception {
        mockMvc.perform(get("/nowhere"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 프론트_오리진의_preflight를_쿠키_포함으로_허용한다() throws Exception {
        mockMvc.perform(options("/api/v1/ping")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void 다른_오리진의_preflight는_거부한다() throws Exception {
        mockMvc.perform(options("/api/v1/ping")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 프록시의_https_헤더로_OAuth_redirect_uri를_만들고_상태는_세션이_아니라_서명_쿠키에_둔다() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao")
                        .param("redirect", "/match/apply")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "api.yufesta.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.containsString("redirect_uri=https://api.yufesta.com/login/oauth2/code/kakao")))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull())
                .andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .anyMatch(cookie -> cookie.startsWith(CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "=")
                                && cookie.contains("HttpOnly") && cookie.contains("Max-Age=300")));
    }

    @Test
    void 허용_목록의_Vercel_오리진도_preflight를_통과한다() throws Exception {
        mockMvc.perform(options("/api/v1/ping")
                        .header(HttpHeaders.ORIGIN, "https://yufesta-web.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://yufesta-web.vercel.app"));
    }
}
