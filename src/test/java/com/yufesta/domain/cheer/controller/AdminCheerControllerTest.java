package com.yufesta.domain.cheer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.cheer.dto.request.UpdateCheerVisibilityRequest;
import com.yufesta.domain.cheer.dto.response.AdminCheerResponse;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import com.yufesta.domain.cheer.service.CheerService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminCheerController.class)
class AdminCheerControllerTest extends ControllerTestSupport {

    @MockitoBean
    private CheerService cheerService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_응원_메시지를_숨기고_복구한다() throws Exception {
        when(cheerService.updateVisibility(eq(1L), any(UpdateCheerVisibilityRequest.class))).thenReturn(response(true));

        mockMvc.perform(patch("/api/v1/admin/cheers/1/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("응원 메시지를 숨겼습니다."))
                .andExpect(jsonPath("$.data.hidden").value(true));

        when(cheerService.updateVisibility(eq(1L), any(UpdateCheerVisibilityRequest.class))).thenReturn(response(false));
        mockMvc.perform(patch("/api/v1/admin/cheers/1/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("응원 메시지를 복구했습니다."))
                .andExpect(jsonPath("$.data.hidden").value(false));
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_운영자_응원_메시지_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/cheers/1/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(cheerService, never()).updateVisibility(eq(1L), any());
    }

    private static AdminCheerResponse response(boolean hidden) {
        return AdminCheerResponse.builder()
                .id(1L)
                .content("축제 최고예요!")
                .displayName("신난 수달")
                .moderationStatus(ModerationStatus.PASSED)
                .reportCount(2)
                .hidden(hidden)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }
}
