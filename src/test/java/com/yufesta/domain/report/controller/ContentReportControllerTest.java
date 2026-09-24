package com.yufesta.domain.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.report.dto.request.CreateContentReportRequest;
import com.yufesta.domain.report.dto.response.ContentReportResponse;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.service.ContentReportService;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = ContentReportController.class)
class ContentReportControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ContentReportService contentReportService;

    @Test
    @WithMockLoginUser(id = 7L)
    void 로그인_사용자가_콘텐츠를_신고한다() throws Exception {
        when(contentReportService.create(eq(7L), any(CreateContentReportRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/content-reports").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "CHEER",
                                  "targetId": 1,
                                  "reason": "부적절한 내용"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.targetType").value("CHEER"));
    }

    @Test
    void 비로그인_사용자가_콘텐츠를_신고하면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/content-reports").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"CHEER\", \"targetId\": 1, \"reason\": \"부적절한 내용\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser
    void 신고_사유가_비어_있으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/content-reports").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"CHEER\", \"targetId\": 1, \"reason\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    private static ContentReportResponse response() {
        return ContentReportResponse.builder()
                .id(1L)
                .targetType(ContentTargetType.CHEER)
                .targetId(1L)
                .reason("부적절한 내용")
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }
}
