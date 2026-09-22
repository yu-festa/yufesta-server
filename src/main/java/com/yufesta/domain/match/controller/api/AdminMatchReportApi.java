package com.yufesta.domain.match.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.dto.request.ReviewMatchReportRequest;
import com.yufesta.domain.match.dto.response.AdminMatchReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 매칭 신고 검토 운영자 API 명세. STAFF 이상(SecurityConfig). 쓰기는 X-XSRF-TOKEN 헤더 필요
 */
@Tag(name = "Admin Match Report", description = "매칭 신고 검토 (FR-MT-42, FR-ADM-03, STAFF)")
@RequestMapping("/api/v1/admin/match/reports")
public interface AdminMatchReportApi {

    @Operation(
            summary = "신고 목록",
            description = "최신순 오프셋 페이지. reviewed 생략 시 전체, false 미검토, true 검토 완료. "
                    + "대상별 유효 신고 수(기각 제외)와 현재 차단 여부를 함께 준다. STAFF. FR-ADM-03"
    )
    @GetMapping
    ApiResponse<List<AdminMatchReportResponse>> getReports(
            @Parameter(description = "검토 여부 필터(선택)") @RequestParam(required = false) Boolean reviewed,
            @Parameter(description = "페이지(0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기(1~50)") @RequestParam(defaultValue = "20") int size
    );

    @Operation(
            summary = "신고 검토",
            description = "CONFIRM: 임계 미달이어도 대상을 즉시 제재(현재 회차 신청 삭제 + 이후 신청 차단). "
                    + "DISMISS: 집계에서 제외하며 임계 아래로 내려가면 제재 해제. 재검토 가능(마지막 결정이 유효). STAFF. FR-MT-42"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "BLOCK_NOT_FOUND")
    @PatchMapping("/{blockId}/review")
    ApiResponse<AdminMatchReportResponse> review(
            @PathVariable Long blockId,
            @Valid @RequestBody ReviewMatchReportRequest request
    );
}
