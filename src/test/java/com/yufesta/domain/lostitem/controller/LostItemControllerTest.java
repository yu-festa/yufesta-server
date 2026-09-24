package com.yufesta.domain.lostitem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.enums.LostItemStatus;
import com.yufesta.domain.lostitem.service.LostItemService;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = LostItemController.class)
class LostItemControllerTest extends ControllerTestSupport {

    @MockitoBean
    private LostItemService lostItemService;

    @Test
    void 비로그인_사용자가_분실물_목록을_조회한다() throws Exception {
        when(lostItemService.getLostItems(20)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/lost-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].kind").value("FOUND"))
                .andExpect(jsonPath("$.data[0].displayName").value("씩씩한 판다"));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 로그인_사용자가_분실물_게시글을_작성한다() throws Exception {
        when(lostItemService.create(eq(7L), any(CreateLostItemRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/v1/lost-items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "FOUND",
                                  "description": "검은색 카드지갑",
                                  "placeText": "중앙도서관 앞",
                                  "occurredAt": "2026-10-02T14:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void 비로그인_사용자가_분실물_게시글을_작성하면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"kind\": \"FOUND\", \"description\": \"카드지갑\", \"placeText\": \"중앙도서관 앞\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 물품_설명이_100자를_넘으면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"kind\": \"FOUND\", \"description\": \"" + "가".repeat(101)
                                + "\", \"placeText\": \"중앙도서관 앞\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    private static LostItemResponse response() {
        return LostItemResponse.builder()
                .id(1L)
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .occurredAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .status(LostItemStatus.OPEN)
                .displayName("씩씩한 판다")
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 5))
                .build();
    }
}
