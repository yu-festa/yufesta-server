package com.yufesta.domain.timetable.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.timetable.dto.response.TimetableResponse;
import com.yufesta.domain.timetable.dto.response.TimetableSlotResponse;
import com.yufesta.domain.timetable.dto.response.TimetableStageResponse;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.service.TimetableService;
import com.yufesta.support.ControllerTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = TimetableController.class)
class TimetableControllerTest extends ControllerTestSupport {

    @MockitoBean
    private TimetableService timetableService;

    @Test
    void 비로그인도_타임테이블을_조회하고_계산_필드가_그대로_나간다() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 16, 20);
        TimetableSlotResponse slot = TimetableSlotResponse.builder()
                .id(4L).sortOrder(4).title("HIPCOM").slotType(SlotType.CLUB)
                .startAt(now.withMinute(15)).endAt(now.withMinute(45))
                .effectiveStartAt(now.withMinute(25)).effectiveEndAt(now.withMinute(55))
                .delayMinutes(10).isLive(false).isChanged(false)
                .stage(TimetableStageResponse.builder().placeId(1L).name("중앙 무대").build())
                .build();
        when(timetableService.getTimetable()).thenReturn(TimetableResponse.of(now, List.of(slot)));

        mockMvc.perform(get("/api/v1/timetable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serverNow").value("2026-10-02T16:20:00"))
                .andExpect(jsonPath("$.data.slots[0].title").value("HIPCOM"))
                .andExpect(jsonPath("$.data.slots[0].slotType").value("CLUB"))
                .andExpect(jsonPath("$.data.slots[0].effectiveStartAt").value("2026-10-02T16:25:00"))
                .andExpect(jsonPath("$.data.slots[0].isLive").value(false))
                .andExpect(jsonPath("$.data.slots[0].isChanged").value(false))
                .andExpect(jsonPath("$.data.slots[0].changedFromStart").doesNotExist())
                .andExpect(jsonPath("$.data.slots[0].stage.name").value("중앙 무대"))
                .andExpect(jsonPath("$.data.slots[0].clubId").doesNotExist());
    }
}
