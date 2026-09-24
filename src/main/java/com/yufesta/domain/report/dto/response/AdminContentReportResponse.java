package com.yufesta.domain.report.dto.response;

import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/** 운영자용 콘텐츠 신고 목록·검토 응답 */
@Builder
public record AdminContentReportResponse(
        @Schema(description = "신고 ID", example = "5") Long id,
        ContentTargetType targetType,
        Long targetId,
        Long reporterUserId,
        String reason,
        LocalDateTime createdAt,
        @Schema(description = "검토 시각. null이면 미검토") LocalDateTime reviewedAt,
        @Schema(description = "현재 대상의 누적 신고 수", example = "2") int targetReportCount,
        @Schema(description = "현재 대상의 비노출 여부", example = "true") boolean targetHidden
) {

    public static AdminContentReportResponse from(ContentReport report, ContentTargetStatus targetStatus) {
        return AdminContentReportResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reporterUserId(report.getReporter() == null ? null : report.getReporter().getId())
                .reason(report.getReason())
                .createdAt(report.getCreatedAt())
                .reviewedAt(report.getReviewedAt())
                .targetReportCount(targetStatus.reportCount())
                .targetHidden(targetStatus.hidden())
                .build();
    }
}
