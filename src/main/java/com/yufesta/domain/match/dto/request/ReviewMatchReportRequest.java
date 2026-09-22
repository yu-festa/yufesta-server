package com.yufesta.domain.match.dto.request;

import com.yufesta.domain.match.enums.BlockDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * 운영자 신고 검토(FR-MT-42). CONFIRM은 즉시 제재, DISMISS는 집계 제외
 */
@Builder
public record ReviewMatchReportRequest(
        @Schema(description = "검토 결과. CONFIRM 제재 확정(앞당김), DISMISS 기각(집계 제외)", example = "CONFIRM")
        @NotNull BlockDecision decision
) {
}
