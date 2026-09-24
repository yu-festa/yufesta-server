package com.yufesta.domain.club.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.club.dto.response.ClubPerformanceResponse;
import com.yufesta.domain.club.dto.response.ClubResponse;
import com.yufesta.domain.club.service.ClubService;
import com.yufesta.support.ControllerTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = ClubController.class)
class ClubControllerTest extends ControllerTestSupport {

    @MockitoBean
    private ClubService clubService;

    @Test
    void 비로그인도_라인업_목록을_조회한다() throws Exception {
        when(clubService.getClubs()).thenReturn(List.of(clubResponse()));

        mockMvc.perform(get("/api/v1/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("HIPCOM"))
                .andExpect(jsonPath("$.data[0].instagramUrl").value("https://www.instagram.com/hipcom_yu"))
                .andExpect(jsonPath("$.data[0].photoUrl").doesNotExist())
                .andExpect(jsonPath("$.data[0].performances[0].slotId").value(4))
                .andExpect(jsonPath("$.data[0].performances[0].effectiveStartAt").value("2026-10-02T16:15:00"))
                .andExpect(jsonPath("$.data[0].performances[0].stageName").value("중앙 무대"));
    }

    @Test
    void 없는_동아리는_404다() throws Exception {
        when(clubService.getClub(99L)).thenThrow(new CustomException(ErrorCode.CLUB_NOT_FOUND));

        mockMvc.perform(get("/api/v1/clubs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
    }

    private static ClubResponse clubResponse() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 2, 16, 15);
        ClubPerformanceResponse performance = ClubPerformanceResponse.builder()
                .slotId(4L).title("HIPCOM").startAt(start).effectiveStartAt(start).endAt(start.plusMinutes(30))
                .stagePlaceId(1L).stageName("중앙 무대")
                .build();
        return ClubResponse.builder()
                .id(3L).name("HIPCOM").intro("영남대학교 유일 힙합 동아리 HIPCOM").genre("힙합")
                .signatureSong("최준현-거북당").instagramUrl("https://www.instagram.com/hipcom_yu")
                .performances(List.of(performance))
                .build();
    }
}
