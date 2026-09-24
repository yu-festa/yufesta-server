package com.yufesta.domain.report.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.report.dto.request.CreateContentReportRequest;
import com.yufesta.domain.report.dto.response.ContentReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 응원 메시지와 분실물 콘텐츠 신고 API 명세 */
@Tag(name = "Content Report", description = "응원 메시지·분실물 콘텐츠 신고 (FR-LF-04, FR-CH-03)")
@RequestMapping("/api/v1/content-reports")
@SecurityRequirement(name = "cookieAuth")
public interface ContentReportApi {

    @Operation(summary = "콘텐츠 신고", description = "로그인 사용자가 응원 메시지 또는 일반 분실물 게시글을 신고한다. CSRF 토큰이 필요하다. FR-LF-04, FR-CH-03")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "CONTENT_REPORT_ALREADY_EXISTS")
    @PostMapping
    ResponseEntity<ApiResponse<ContentReportResponse>> createContentReport(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateContentReportRequest request
    );
}
