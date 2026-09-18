package com.yufesta.domain.match.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.match.dto.response.LastResultResponse;
import com.yufesta.domain.match.dto.response.MatchRoundResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MySummaryResponse;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.service.MatchSummaryService;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = MatchController.class)
class MatchControllerTest extends ControllerTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 15, 30);

    @MockitoBean
    private MatchSummaryService matchSummaryService;

    @Test
    void 비로그인도_홈_블록을_조회하고_시각은_오프셋_없이_내려간다() throws Exception {
        when(matchSummaryService.getSummary(isNull())).thenReturn(summary(null));

        mockMvc.perform(get("/api/v1/match/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverNow").value("2026-10-02T15:30:00"))
                .andExpect(jsonPath("$.data.currentRound.seq").value(1))
                .andExpect(jsonPath("$.data.currentRound.status").value("OPEN"))
                .andExpect(jsonPath("$.data.currentRound.publishAt").value("2026-10-02T16:00:00"))
                .andExpect(jsonPath("$.data.applicantCount").value(137))
                .andExpect(jsonPath("$.data.my").doesNotExist());
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 로그인_사용자는_principal이_전달되어_my가_채워진다() throws Exception {
        MySummaryResponse my = MySummaryResponse.of(true, LastResultResponse.of(1, MatchResultStatus.MATCHED));
        when(matchSummaryService.getSummary(7L)).thenReturn(summary(my));

        mockMvc.perform(get("/api/v1/match/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.my.applied").value(true))
                .andExpect(jsonPath("$.data.my.lastResult.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.my.userId").doesNotExist());
    }

    @Test
    void 관심_태그는_10개를_코드와_라벨로_준다() throws Exception {
        mockMvc.perform(get("/api/v1/match/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10))
                .andExpect(jsonPath("$.data[0].code").value("ALCOHOL"))
                .andExpect(jsonPath("$.data[0].label").value("술"))
                .andExpect(jsonPath("$.data[9].code").value("ETC"));
    }

    private static MatchSummaryResponse summary(MySummaryResponse my) {
        MatchRoundResponse current = MatchRoundResponse.builder()
                .seq(1).status(RoundStatus.OPEN)
                .openAt(LocalDateTime.of(2026, 9, 25, 0, 0))
                .closeAt(LocalDateTime.of(2026, 10, 2, 15, 50))
                .publishAt(LocalDateTime.of(2026, 10, 2, 16, 0))
                .build();
        return MatchSummaryResponse.builder()
                .serverNow(NOW)
                .currentRound(current)
                .nextRound(null)
                .applicantCount(137)
                .my(my)
                .build();
    }
}
