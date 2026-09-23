package com.yufesta.domain.match.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.match.dto.request.UpdateRoundTimesRequest;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.service.MatchRoundAdminService;
import com.yufesta.domain.match.service.MatchRoundBatchService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminMatchController.class)
class AdminMatchControllerTest extends ControllerTestSupport {

    @MockitoBean
    private MatchRoundAdminService matchRoundAdminService;

    @MockitoBean
    private MatchRoundBatchService matchRoundBatchService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_마감_배치를_실행하고_결과를_받는다() throws Exception {
        when(matchRoundBatchService.close(1L)).thenReturn(result());

        mockMvc.perform(post("/api/v1/admin/match/rounds/1/close").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pairCount").value(30))
                .andExpect(jsonPath("$.data.averageScore").value(2.35));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_발표할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/match/rounds/1/publish").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(matchRoundBatchService, never()).publish(any());
    }

    @Test
    @WithMockLoginUser(role = UserRole.OWNER)
    void OWNER는_발표할_수_있다() throws Exception {
        when(matchRoundBatchService.publish(1L)).thenReturn(round(RoundStatus.PUBLISHED));

        mockMvc.perform(post("/api/v1/admin/match/rounds/1/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_운영자_API에_403이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/match/rounds"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비로그인은_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/match/rounds"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void 시각_수정은_세_값이_모두_필요하고_서비스에_전달된다() throws Exception {
        when(matchRoundAdminService.updateTimes(eq(1L), any(UpdateRoundTimesRequest.class))).thenReturn(round(RoundStatus.OPEN));

        mockMvc.perform(patch("/api/v1/admin/match/rounds/1/times").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openAt\": \"2026-09-25T00:00:00\", \"closeAt\": \"2026-10-02T15:50:00\", \"publishAt\": \"2026-10-02T16:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closeAt").value("2026-10-02T15:50:00"));

        mockMvc.perform(patch("/api/v1/admin/match/rounds/1/times").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openAt\": \"2026-09-25T00:00:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    private static AdminMatchRoundResponse round(RoundStatus status) {
        return AdminMatchRoundResponse.builder()
                .id(1L).seq(1).status(status)
                .openAt(LocalDateTime.of(2026, 9, 25, 0, 0))
                .closeAt(LocalDateTime.of(2026, 10, 2, 15, 50))
                .publishAt(LocalDateTime.of(2026, 10, 2, 16, 0))
                .build();
    }

    private static RoundBatchResultResponse result() {
        return RoundBatchResultResponse.builder()
                .roundSeq(1).status(RoundStatus.CLOSED).poolSize(80).poolMen(50).poolWomen(30)
                .matchedApplicants(80).unmatchedApplicants(0).pairCount(30).firstPassPairs(30).secondPassPairs(0)
                .averageScore(new BigDecimal("2.35"))
                .build();
    }
}
