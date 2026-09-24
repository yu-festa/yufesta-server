package com.yufesta.domain.timetable.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.timetable.dto.request.ChangeSlotTimesRequest;
import com.yufesta.domain.timetable.dto.request.CreateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.request.LiveSlotRequest;
import com.yufesta.domain.timetable.dto.request.ReorderSlotsRequest;
import com.yufesta.domain.timetable.dto.response.AdminTimetableSlotResponse;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.service.TimetableAdminService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminTimetableController.class)
class AdminTimetableControllerTest extends ControllerTestSupport {

    private static final String CREATE_JSON = """
            {"title":"HIPCOM","slotType":"CLUB","startAt":"2026-10-02T16:15:00","endAt":"2026-10-02T16:45:00",
             "stagePlaceId":1,"sortOrder":4}
            """;

    @MockitoBean
    private TimetableAdminService timetableAdminService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_공연을_등록한다() throws Exception {
        when(timetableAdminService.create(any(CreateTimetableSlotRequest.class))).thenReturn(slotResponse());

        mockMvc.perform(post("/api/v1/admin/timetable")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("공연을 등록했습니다."))
                .andExpect(jsonPath("$.data.title").value("HIPCOM"))
                .andExpect(jsonPath("$.data.stageName").value("중앙 무대"));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void 필수값이_빠지면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/timetable")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"slotType\":\"CLUB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockLoginUser(role = UserRole.OWNER)
    void OWNER는_공연_시각을_바꾼다() throws Exception {
        when(timetableAdminService.changeTimes(eq(4L), any(ChangeSlotTimesRequest.class))).thenReturn(slotResponse());

        mockMvc.perform(patch("/api/v1/admin/timetable/4/times")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"2026-10-02T16:30:00\",\"endAt\":\"2026-10-02T17:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("공연 시각을 변경했습니다."));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_진행_중을_지정한다() throws Exception {
        when(timetableAdminService.setLive(eq(4L), any(LiveSlotRequest.class))).thenReturn(slotResponse());

        mockMvc.perform(patch("/api/v1/admin/timetable/4/live")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"live\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(4));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_순서를_일괄_변경한다() throws Exception {
        when(timetableAdminService.reorder(any(ReorderSlotsRequest.class))).thenReturn(List.of(slotResponse()));

        mockMvc.perform(put("/api/v1/admin/timetable/order")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotIds\":[4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(4));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_공연을_삭제하면_204다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/timetable/4").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void USER는_타임테이블_운영_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/timetable")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static AdminTimetableSlotResponse slotResponse() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 2, 16, 15);
        return AdminTimetableSlotResponse.builder()
                .id(4L).sortOrder(4).title("HIPCOM").slotType(SlotType.CLUB)
                .startAt(start).endAt(start.plusMinutes(30))
                .liveOverride(false).stagePlaceId(1L).stageName("중앙 무대")
                .build();
    }
}
