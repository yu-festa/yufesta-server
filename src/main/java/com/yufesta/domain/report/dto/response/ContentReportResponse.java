package com.yufesta.domain.report.dto.response;

import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import java.time.LocalDateTime;
import lombok.Builder;

/** 사용자에게 반환하는 콘텐츠 신고 접수 결과 */
@Builder
public record ContentReportResponse(
        Long id,
        ContentTargetType targetType,
        Long targetId,
        String reason,
        LocalDateTime createdAt
) {

    public static ContentReportResponse from(ContentReport report) {
        return ContentReportResponse.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
