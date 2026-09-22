package com.yufesta.domain.match.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.request.ApplyMatchRequest;
import com.yufesta.domain.match.dto.response.ApplicationResponse;
import com.yufesta.domain.match.dto.response.LastResultResponse;
import com.yufesta.domain.match.dto.response.MatchResultResponse;
import com.yufesta.domain.match.dto.response.MatchRoundResponse;
import com.yufesta.domain.match.dto.response.PartnerCardResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MySummaryResponse;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.service.ApplicationService;
import com.yufesta.domain.match.service.MatchResultService;
import com.yufesta.domain.match.service.MatchSummaryService;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = MatchController.class)
class MatchControllerTest extends ControllerTestSupport {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 15, 30);

    @MockitoBean
    private MatchSummaryService matchSummaryService;

    @MockitoBean
    private ApplicationService applicationService;

    @MockitoBean
    private MatchResultService matchResultService;

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

    @Test
    @WithMockLoginUser(id = 7L)
    void 신청하면_201과_정규화된_응답을_주고_나이대는_ERD_값으로_오간다() throws Exception {
        when(applicationService.apply(eq(7L), any(ApplyMatchRequest.class))).thenReturn(applicationResponse());

        mockMvc.perform(post("/api/v1/match/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instagramId": "@Yu.Festa", "nickname": "펭귄", "gender": "F", "ageBand": "22-24",
                                 "tags": ["MUSIC", "CAFE"], "intro": "같이 공연 봐요",
                                 "termsVersion": "v1", "privacyVersion": "v1", "ageConfirmed": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.instagramId").value("yu.festa"))
                .andExpect(jsonPath("$.data.ageBand").value("22-24"))
                .andExpect(jsonPath("$.data.userId").doesNotExist());

        verify(applicationService).apply(eq(7L), any(ApplyMatchRequest.class));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 형식_오류는_서비스에_가기_전에_400과_필드명으로_막힌다() throws Exception {
        mockMvc.perform(post("/api/v1/match/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instagramId": "yu.festa", "nickname": "펭", "gender": "F",
                                 "tags": ["MUSIC", "CAFE", "PET", "GAME"],
                                 "termsVersion": "v1", "privacyVersion": "v1", "ageConfirmed": false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[*].field").value(
                        org.hamcrest.Matchers.containsInAnyOrder("nickname", "tags", "ageConfirmed")));

        verify(applicationService, org.mockito.Mockito.never()).apply(any(), any());
    }

    @Test
    void 비로그인_신청은_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/match/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 내_신청_조회와_취소() throws Exception {
        when(applicationService.getMine(7L)).thenReturn(applicationResponse());

        mockMvc.perform(get("/api/v1/match/applications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roundSeq").value(1))
                .andExpect(jsonPath("$.data.tags[0]").value("CAFE"));

        mockMvc.perform(delete("/api/v1/match/applications/me").with(csrf()))
                .andExpect(status().isNoContent());
        verify(applicationService).cancel(7L);
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 내_결과는_회차_지정이_없으면_null로_넘기고_카드에_식별_정보가_없다() throws Exception {
        PartnerCardResponse card = PartnerCardResponse.builder()
                .matchId(77L).nickname("수달").ageBand(AgeBand.A22_24)
                .tags(List.of(MatchTag.MUSIC)).commonTags(List.of(MatchTag.MUSIC)).intro("안녕").instagramId("otter")
                .build();
        when(matchResultService.getMyResult(7L, null)).thenReturn(MatchResultResponse.builder()
                .roundSeq(1).status(MatchResultStatus.MATCHED).partners(List.of(card))
                .nextRoundSeq(2).hasNextRoundApplication(false).canRejoin(true).build());

        mockMvc.perform(get("/api/v1/match/results/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATCHED"))
                .andExpect(jsonPath("$.data.partners[0].matchId").value(77))
                .andExpect(jsonPath("$.data.partners[0].instagramId").value("otter"))
                .andExpect(jsonPath("$.data.partners[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.partners[0].score").doesNotExist())
                .andExpect(jsonPath("$.data.canRejoin").value(true));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 회차_번호를_지정하면_그대로_전달되고_발표_전이면_409다() throws Exception {
        when(matchResultService.getMyResult(7L, 1)).thenThrow(new CustomException(ErrorCode.MATCH_RESULT_NOT_PUBLISHED));

        mockMvc.perform(get("/api/v1/match/results/me").param("roundSeq", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_RESULT_NOT_PUBLISHED"));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 재참여는_201이다() throws Exception {
        when(applicationService.rejoin(7L)).thenReturn(applicationResponse());

        mockMvc.perform(post("/api/v1/match/applications/rejoin").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.roundSeq").value(1));
    }

    private static ApplicationResponse applicationResponse() {
        return ApplicationResponse.builder()
                .id(42L).roundSeq(1).instagramId("yu.festa").nickname("펭귄").gender(Gender.F)
                .ageBand(AgeBand.A22_24).tags(List.of(MatchTag.CAFE, MatchTag.MUSIC)).intro("같이 공연 봐요")
                .entryType(EntryType.NEW).createdAt(NOW)
                .build();
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
