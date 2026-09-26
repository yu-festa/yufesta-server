package com.yufesta.domain.lostitem.comment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.lostitem.comment.dto.request.UpdateLostItemCommentVisibilityRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import com.yufesta.domain.lostitem.comment.service.LostItemCommentService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminLostItemCommentController.class)
class AdminLostItemCommentControllerTest extends ControllerTestSupport {

    @MockitoBean
    private LostItemCommentService lostItemCommentService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_분실물_댓글을_숨긴다() throws Exception {
        when(lostItemCommentService.updateVisibility(eq(1L), eq(10L), any(UpdateLostItemCommentVisibilityRequest.class)))
                .thenReturn(response());

        mockMvc.perform(patch("/api/v1/admin/lost-items/1/comments/10/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("분실물 댓글을 숨겼습니다."));
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_운영자_분실물_댓글_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/lost-items/1/comments/10/visibility").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(lostItemCommentService, never()).updateVisibility(any(), any(), any());
    }

    private static LostItemCommentResponse response() {
        return LostItemCommentResponse.builder()
                .id(10L)
                .content("댓글")
                .displayName("수줍은 펭귄")
                .mine(false)
                .postAuthor(false)
                .deleted(false)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .replies(List.of())
                .build();
    }
}
