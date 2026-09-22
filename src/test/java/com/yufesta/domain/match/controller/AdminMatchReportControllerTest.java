package com.yufesta.domain.match.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.match.dto.request.ReviewMatchReportRequest;
import com.yufesta.domain.match.dto.response.AdminMatchReportResponse;
import com.yufesta.domain.match.enums.BlockDecision;
import com.yufesta.domain.match.enums.BlockReason;
import com.yufesta.domain.match.service.MatchReportService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminMatchReportController.class)
class AdminMatchReportControllerTest extends ControllerTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 16, 30);

    @MockitoBean
    private MatchReportService matchReportService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_미검토_목록을_조회하고_기본_페이지는_0과_20이다() throws Exception {
        when(matchReportService.getReports(false, 0, 20)).thenReturn(List.of(report(null)));

        mockMvc.perform(get("/api/v1/admin/match/reports").param("reviewed", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].targetUserId").value(201))
                .andExpect(jsonPath("$.data[0].targetReportCount").value(2))
                .andExpect(jsonPath("$.data[0].targetBlocked").value(true))
                .andExpect(jsonPath("$.data[0].decision").doesNotExist());
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void 필터를_생략하면_null로_넘긴다() throws Exception {
        when(matchReportService.getReports(null, 1, 50)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/match/reports").param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_신고를_검토한다() throws Exception {
        when(matchReportService.review(eq(5L), any(ReviewMatchReportRequest.class))).thenReturn(report(BlockDecision.CONFIRM));

        mockMvc.perform(patch("/api/v1/admin/match/reports/5/review").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"CONFIRM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.decision").value("CONFIRM"))
                .andExpect(jsonPath("$.data.targetBlocked").value(true));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void 결정이_없으면_400이다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/match/reports/5/review").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        verify(matchReportService, never()).review(any(), any());
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/match/reports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/match/reports"))
                .andExpect(status().isUnauthorized());
    }

    private static AdminMatchReportResponse report(BlockDecision decision) {
        return AdminMatchReportResponse.builder()
                .id(5L).roundSeq(1).reporterUserId(7L).targetUserId(201L)
                .reason(BlockReason.FAKE).detail("프로필과 달라요").createdAt(NOW)
                .reviewedAt(decision == null ? null : NOW).decision(decision)
                .targetReportCount(2L).targetBlocked(true)
                .build();
    }
}
