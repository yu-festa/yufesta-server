package com.yufesta.domain.lostitem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.lostitem.dto.request.CreateOfficialLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.UpdateLostItemVisibilityRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.enums.LostItemStatus;
import com.yufesta.domain.lostitem.service.LostItemService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminLostItemController.class)
class AdminLostItemControllerTest extends ControllerTestSupport {

    @MockitoBean
    private LostItemService lostItemService;

    @Test
    @WithMockLoginUser(id = 7L, role = UserRole.STAFF)
    void STAFF는_안내소_습득물을_등록한다() throws Exception {
        when(lostItemService.createOfficial(eq(7L), any(CreateOfficialLostItemRequest.class))).thenReturn(officialResponse());

        mockMvc.perform(post("/api/v1/admin/lost-items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "검은색 카드지갑",
                                  "placeText": "중앙도서관 앞",
                                  "occurredAt": "2026-10-02T14:00:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("FOUND"))
                .andExpect(jsonPath("$.data.displayName").value("종합 안내소"));
    }

    @Test
    @WithMockLoginUser(id = 7L, role = UserRole.OWNER)
    void OWNER는_분실물_게시글을_해결_처리한다() throws Exception {
        when(lostItemService.resolveByAdmin(7L, 1L)).thenReturn(resolvedResponse());

        mockMvc.perform(patch("/api/v1/admin/lost-items/1/resolve").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    @WithMockLoginUser(id = 7L, role = UserRole.STAFF)
    void STAFF는_일반_분실물_게시글을_숨기고_복구한다() throws Exception {
        when(lostItemService.updateVisibility(eq(7L), eq(1L), any(UpdateLostItemVisibilityRequest.class)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/v1/admin/lost-items/1/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분실물 게시글을 숨겼습니다."));

        mockMvc.perform(patch("/api/v1/admin/lost-items/1/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분실물 게시글을 복구했습니다."));
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_운영자_분실물_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/lost-items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(lostItemService, never()).createOfficial(any(), any());
    }

    private static LostItemResponse officialResponse() {
        return LostItemResponse.builder()
                .id(1L)
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .status(LostItemStatus.OPEN)
                .displayName("종합 안내소")
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 5))
                .build();
    }

    private static LostItemResponse response() {
        return LostItemResponse.builder()
                .id(1L)
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .status(LostItemStatus.OPEN)
                .displayName("씩씩한 판다")
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 5))
                .build();
    }

    private static LostItemResponse resolvedResponse() {
        return LostItemResponse.builder()
                .id(1L)
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .status(LostItemStatus.RESOLVED)
                .displayName("씩씩한 판다")
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 5))
                .build();
    }
}
