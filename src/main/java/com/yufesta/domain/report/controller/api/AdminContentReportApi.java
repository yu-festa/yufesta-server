package com.yufesta.domain.report.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.report.dto.response.AdminContentReportResponse;
import com.yufesta.domain.report.enums.ContentTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 운영자 콘텐츠 신고 관리 API 명세. STAFF 이상은 SecurityConfig에서 인가한다. */
@Tag(name = "Admin Content Report", description = "운영자 콘텐츠 신고 관리 (FR-ADM-04, STAFF)")
@RequestMapping("/api/v1/admin/content-reports")
public interface AdminContentReportApi {

    @Operation(summary = "콘텐츠 신고 목록", description = "최신순 오프셋 페이지. reviewed와 targetType은 선택 필터다. 대상의 현재 비노출 여부와 누적 신고 수를 함께 제공한다.")
    @GetMapping
    ApiResponse<List<AdminContentReportResponse>> getReports(
            @RequestParam(required = false) Boolean reviewed,
            @RequestParam(required = false) ContentTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "콘텐츠 신고 검토", description = "신고를 확인하고 reviewedAt을 기록한다. 현재 스키마에는 별도 처리 결과 컬럼이 없다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CONTENT_REPORT_NOT_FOUND")
    @PatchMapping("/{reportId}/review")
    ApiResponse<AdminContentReportResponse> review(@PathVariable Long reportId);
}
