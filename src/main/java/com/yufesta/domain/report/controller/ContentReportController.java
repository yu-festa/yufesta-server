package com.yufesta.domain.report.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.report.controller.api.ContentReportApi;
import com.yufesta.domain.report.dto.request.CreateContentReportRequest;
import com.yufesta.domain.report.dto.response.ContentReportResponse;
import com.yufesta.domain.report.service.ContentReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 콘텐츠 신고 API. 명세는 ContentReportApi */
@RestController
public class ContentReportController implements ContentReportApi {

    private final ContentReportService contentReportService;

    public ContentReportController(ContentReportService contentReportService) {
        this.contentReportService = contentReportService;
    }

    @Override
    public ResponseEntity<ApiResponse<ContentReportResponse>> createContentReport(
            Long userId,
            CreateContentReportRequest request
    ) {
        ContentReportResponse report = contentReportService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "신고가 접수되었어요.", report));
    }
}
