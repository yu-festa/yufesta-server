package com.yufesta.domain.notice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.notice.dto.request.CreateNoticeRequest;
import com.yufesta.domain.notice.dto.request.UpdateNoticeRequest;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.service.NoticeService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminNoticeController.class)
class AdminNoticeControllerTest extends ControllerTestSupport {

    @MockitoBean
    private NoticeService noticeService;

    @Test
    @WithMockLoginUser(id = 7L, role = UserRole.STAFF)
    void STAFF는_공지를_등록한다() throws Exception {
        when(noticeService.create(eq(7L), any(CreateNoticeRequest.class))).thenReturn(noticeResponse(true));

        mockMvc.perform(post("/api/v1/admin/notices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"우천 시 공연 안내\", \"body\": \"우천 시 공연장 안내입니다.\", \"banner\": true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.banner").value(true));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_공지를_수정한다() throws Exception {
        when(noticeService.update(eq(1L), eq(1L), any(UpdateNoticeRequest.class))).thenReturn(noticeResponse(false));

        mockMvc.perform(patch("/api/v1/admin/notices/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"변경된 축제 안내\", \"body\": \"변경된 공지 본문\", \"banner\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("축제 안내"));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_공지를_삭제한다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/notices/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(noticeService).delete(1L, 1L);
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_운영자_공지_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(noticeService, never()).create(any(), any());
    }

    @Test
    void 비로그인은_운영자_공지_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void CSRF_토큰_없이_공지_등록하면_403이다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"우천 시 공연 안내\", \"body\": \"우천 시 공연장 안내입니다.\", \"banner\": true}"))
                .andExpect(status().isForbidden());

        verify(noticeService, never()).create(any(), any());
    }

    private static NoticeResponse noticeResponse(boolean banner) {
        return NoticeResponse.builder()
                .id(1L)
                .title("축제 안내")
                .body("축제 공지 본문")
                .banner(banner)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }
}
