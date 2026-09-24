package com.yufesta.domain.notice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.service.NoticeService;
import com.yufesta.support.ControllerTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = NoticeController.class)
class NoticeControllerTest extends ControllerTestSupport {

    @MockitoBean
    private NoticeService noticeService;

    @Test
    void 비로그인도_최신순_공지_목록을_조회한다() throws Exception {
        when(noticeService.getNotices(20)).thenReturn(List.of(noticeResponse()));

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("축제 안내"))
                .andExpect(jsonPath("$.data[0].banner").value(false));
    }

    @Test
    void 요청한_개수만큼_공지_목록을_조회한다() throws Exception {
        when(noticeService.getNotices(5)).thenReturn(List.of(noticeResponse()));

        mockMvc.perform(get("/api/v1/notices").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 공지_목록_개수가_범위를_벗어나면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/notices").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비로그인도_공지_상세를_조회한다() throws Exception {
        when(noticeService.getNotice(1L)).thenReturn(noticeResponse());

        mockMvc.perform(get("/api/v1/notices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("축제 공지 본문"));
    }

    private static NoticeResponse noticeResponse() {
        return NoticeResponse.builder()
                .id(1L)
                .title("축제 안내")
                .body("축제 공지 본문")
                .banner(false)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }
}
