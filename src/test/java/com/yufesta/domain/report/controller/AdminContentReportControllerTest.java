package com.yufesta.domain.report.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.report.dto.response.AdminContentReportResponse;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.service.ContentReportService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminContentReportController.class)
class AdminContentReportControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ContentReportService contentReportService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_필터를_적용해_콘텐츠_신고_목록을_조회한다() throws Exception {
        when(contentReportService.getReports(false, ContentTargetType.CHEER, 1, 20)).thenReturn(List.of(response(null)));

        mockMvc.perform(get("/api/v1/admin/content-reports")
                        .param("reviewed", "false")
                        .param("targetType", "CHEER")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetType").value("CHEER"))
                .andExpect(jsonPath("$.data[0].targetReportCount").value(2))
                .andExpect(jsonPath("$.data[0].targetHidden").value(true));
    }

    @Test
    @WithMockLoginUser(role = UserRole.OWNER)
    void OWNER는_콘텐츠_신고를_검토한다() throws Exception {
        when(contentReportService.review(5L)).thenReturn(response(LocalDateTime.of(2026, 10, 3, 10, 0)));

        mockMvc.perform(patch("/api/v1/admin/content-reports/5/review").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("콘텐츠 신고를 검토 처리했습니다."))
                .andExpect(jsonPath("$.data.reviewedAt").isNotEmpty());
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_운영자_콘텐츠_신고_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/content-reports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(contentReportService, never()).getReports(eq(null), eq(null), eq(0), eq(20));
    }

    private static AdminContentReportResponse response(LocalDateTime reviewedAt) {
        return AdminContentReportResponse.builder()
                .id(5L)
                .targetType(ContentTargetType.CHEER)
                .targetId(10L)
                .reporterUserId(7L)
                .reason("부적절한 내용")
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .reviewedAt(reviewedAt)
                .targetReportCount(2)
                .targetHidden(true)
                .build();
    }
}
