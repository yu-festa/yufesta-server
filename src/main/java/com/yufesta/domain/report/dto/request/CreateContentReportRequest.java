package com.yufesta.domain.report.dto.request;

import com.yufesta.domain.report.enums.ContentTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 콘텐츠 신고 등록 요청 */
@Builder
public record CreateContentReportRequest(
        @Schema(description = "신고 대상 유형", example = "CHEER") @NotNull ContentTargetType targetType,
        @Schema(description = "신고 대상 ID", example = "1") @NotNull @Positive Long targetId,
        @Schema(description = "신고 사유", example = "부적절한 내용") @NotBlank @Size(max = 20) String reason
) {
}
