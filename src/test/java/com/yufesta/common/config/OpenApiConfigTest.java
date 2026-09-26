package com.yufesta.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 생성된 OpenAPI 문서가 쿠키 인증 스키마·공통 오류·그룹 분리를 담는지 확인. 로그인 필요 판단은 SecurityConfig §7 규칙과 같아야 한다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void public_그룹은_쿠키_스키마와_오류_스키마를_갖고_admin_경로를_제외한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("access_token"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.components.schemas.ValidationError").exists())
                .andExpect(jsonPath("$.paths['/api/v1/match/summary']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/match/rounds']").doesNotExist());
    }

    @Test
    void 쓰기와_본인_자원은_로그인_표시와_401이_붙고_공개_읽기에는_없다() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(jsonPath("$.paths['/api/v1/match/applications'].post.security[0].cookieAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/match/applications'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/match/applications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/match/results/me'].get.security[0].cookieAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/match/summary'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/match/summary'].get.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/match/summary'].get.responses['500'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security").doesNotExist());
    }

    @Test
    void admin_그룹은_admin_경로만_담고_403이_붙는다() throws Exception {
        mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/match/rounds'].get.security[0].cookieAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/match/rounds'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/match/summary']").doesNotExist());
    }
}
