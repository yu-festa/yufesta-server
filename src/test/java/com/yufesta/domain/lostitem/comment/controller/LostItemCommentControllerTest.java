package com.yufesta.domain.lostitem.comment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.lostitem.comment.dto.request.CreateLostItemCommentRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import com.yufesta.domain.lostitem.comment.service.LostItemCommentService;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = LostItemCommentController.class)
class LostItemCommentControllerTest extends ControllerTestSupport {

    @MockitoBean
    private LostItemCommentService lostItemCommentService;

    @Test
    void 비로그인_사용자가_분실물_댓글과_답글을_조회한다() throws Exception {
        when(lostItemCommentService.getComments(null, 1L)).thenReturn(List.of(commentResponse()));

        mockMvc.perform(get("/api/v1/lost-items/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].displayName").value("수줍은 펭귄"))
                .andExpect(jsonPath("$.data[0].postAuthor").value(true))
                .andExpect(jsonPath("$.data[0].replies[0].content").value("감사합니다."));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 로그인_사용자가_분실물_댓글을_작성한다() throws Exception {
        when(lostItemCommentService.create(eq(7L), eq(1L), any(CreateLostItemCommentRequest.class)))
                .thenReturn(commentResponse());

        mockMvc.perform(post("/api/v1/lost-items/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안내소에 맡겼어요.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("댓글을 등록했습니다."));
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 로그인_사용자가_최상위_댓글에_답글을_작성한다() throws Exception {
        when(lostItemCommentService.reply(eq(7L), eq(1L), eq(10L), any(CreateLostItemCommentRequest.class)))
                .thenReturn(replyResponse());

        mockMvc.perform(post("/api/v1/lost-items/1/comments/10/replies").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"감사합니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("답글을 등록했습니다."))
                .andExpect(jsonPath("$.data.parentId").value(10));
    }

    @Test
    void 비로그인_사용자가_분실물_댓글을_작성하면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items/1/comments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"안내소에 맡겼어요.\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 작성자가_분실물_댓글을_삭제한다() throws Exception {
        mockMvc.perform(delete("/api/v1/lost-items/1/comments/10").with(csrf()))
                .andExpect(status().isNoContent());
    }

    private static LostItemCommentResponse commentResponse() {
        return LostItemCommentResponse.builder()
                .id(10L)
                .content("안내소에 맡겼어요.")
                .displayName("수줍은 펭귄")
                .mine(true)
                .postAuthor(true)
                .deleted(false)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .replies(List.of(replyResponse()))
                .build();
    }

    private static LostItemCommentResponse replyResponse() {
        return LostItemCommentResponse.builder()
                .id(11L)
                .parentId(10L)
                .content("감사합니다.")
                .displayName("씩씩한 판다")
                .mine(false)
                .postAuthor(false)
                .deleted(false)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 1))
                .replies(List.of())
                .build();
    }
}
