package com.yufesta.domain.match.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.controller.api.AdminMatchReportApi;
import com.yufesta.domain.match.dto.request.ReviewMatchReportRequest;
import com.yufesta.domain.match.dto.response.AdminMatchReportResponse;
import com.yufesta.domain.match.service.MatchReportService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭 신고 검토 운영자 API. 명세는 AdminMatchReportApi
 */
@RestController
public class AdminMatchReportController implements AdminMatchReportApi {

    private final MatchReportService matchReportService;

    public AdminMatchReportController(MatchReportService matchReportService) {
        this.matchReportService = matchReportService;
    }

    @Override
    public ApiResponse<List<AdminMatchReportResponse>> getReports(Boolean reviewed, int page, int size) {
        return ApiResponse.success(matchReportService.getReports(reviewed, page, size));
    }

    @Override
    public ApiResponse<AdminMatchReportResponse> review(Long blockId, ReviewMatchReportRequest request) {
        return ApiResponse.success(matchReportService.review(blockId, request));
    }
}
