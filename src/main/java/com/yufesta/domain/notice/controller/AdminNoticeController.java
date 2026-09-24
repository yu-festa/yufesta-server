package com.yufesta.domain.notice.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.notice.controller.api.AdminNoticeApi;
import com.yufesta.domain.notice.dto.request.CreateNoticeRequest;
import com.yufesta.domain.notice.dto.request.UpdateNoticeRequest;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.service.NoticeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 공지 관리 API. 명세는 AdminNoticeApi */
@RestController
public class AdminNoticeController implements AdminNoticeApi {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Override
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(Long userId, CreateNoticeRequest request) {
        NoticeResponse response = noticeService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "공지를 등록했습니다.", response));
    }

    @Override
    public ApiResponse<NoticeResponse> updateNotice(Long userId, Long noticeId, UpdateNoticeRequest request) {
        return ApiResponse.success("공지를 수정했습니다.", noticeService.update(userId, noticeId, request));
    }

    @Override
    public void deleteNotice(Long userId, Long noticeId) {
        noticeService.delete(userId, noticeId);
    }
}
