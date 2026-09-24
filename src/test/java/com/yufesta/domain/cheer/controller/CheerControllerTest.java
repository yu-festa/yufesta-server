package com.yufesta.domain.cheer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.cheer.dto.request.CreateCheerRequest;
import com.yufesta.domain.cheer.dto.response.CheerResponse;
import com.yufesta.domain.cheer.service.AnonymousKeyService;
import com.yufesta.domain.cheer.service.CheerService;
import com.yufesta.support.ControllerTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = CheerController.class)
class CheerControllerTest extends ControllerTestSupport {

    @MockitoBean
    private CheerService cheerService;

    @MockitoBean
    private AnonymousKeyService anonymousKeyService;

    @Test
    void 비로그인_사용자가_응원_메시지를_조회한다() throws Exception {
        when(cheerService.getCheers(null, 20)).thenReturn(List.of(response(false)));

        mockMvc.perform(get("/api/v1/cheers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("축제 파이팅!"))
                .andExpect(jsonPath("$.data[0].displayName").value("씩씩한 판다"))
                .andExpect(jsonPath("$.data[0].mine").value(false));
    }

    @Test
    void 비로그인_사용자가_익명_키를_발급받아_응원_메시지를_작성한다() throws Exception {
        when(anonymousKeyService.resolve(any(), any())).thenReturn("anon-key");
        when(cheerService.create(eq("anon-key"), any(CreateCheerRequest.class))).thenReturn(response(true));

        mockMvc.perform(post("/api/v1/cheers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"축제 파이팅!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.mine").value(true));
    }

    @Test
    void 응원_메시지가_40자를_넘으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/cheers").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + "가".repeat(41) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void CSRF_토큰_없이_응원_메시지를_작성하면_403이다() throws Exception {
        mockMvc.perform(post("/api/v1/cheers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"축제 파이팅!\"}"))
                .andExpect(status().isForbidden());
    }

    private static CheerResponse response(boolean mine) {
        return CheerResponse.builder()
                .id(1L)
                .content("축제 파이팅!")
                .displayName("씩씩한 판다")
                .mine(mine)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }
}
