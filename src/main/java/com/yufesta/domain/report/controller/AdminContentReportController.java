package com.yufesta.domain.report.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.report.controller.api.AdminContentReportApi;
import com.yufesta.domain.report.dto.response.AdminContentReportResponse;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.service.ContentReportService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 콘텐츠 신고 관리 API. 명세는 AdminContentReportApi */
@RestController
public class AdminContentReportController implements AdminContentReportApi {

    private final ContentReportService contentReportService;

    public AdminContentReportController(ContentReportService contentReportService) {
        this.contentReportService = contentReportService;
    }

    @Override
    public ApiResponse<List<AdminContentReportResponse>> getReports(
            Boolean reviewed,
            ContentTargetType targetType,
            int page,
            int size
    ) {
        return ApiResponse.success(contentReportService.getReports(reviewed, targetType, page, size));
    }

    @Override
    public ApiResponse<AdminContentReportResponse> review(Long reportId) {
        return ApiResponse.success("콘텐츠 신고를 검토 처리했습니다.", contentReportService.review(reportId));
    }
}
