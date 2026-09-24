package com.yufesta.domain.notice.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.notice.controller.api.NoticeApi;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.service.NoticeService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지 공개 API. 명세는 NoticeApi
 */
@RestController
public class NoticeController implements NoticeApi {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Override
    public ApiResponse<List<NoticeResponse>> getNotices(int size) {
        return ApiResponse.success(noticeService.getNotices(size));
    }

    @Override
    public ApiResponse<NoticeResponse> getNotice(Long noticeId) {
        return ApiResponse.success(noticeService.getNotice(noticeId));
    }
}
