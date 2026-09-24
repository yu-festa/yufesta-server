package com.yufesta.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;

/** 공연 지연 표시 요청 */
@Builder
public record DelaySlotRequest(
        @Schema(description = "지연 분(0~600). null이면 지연 해제", example = "10") @Min(0) @Max(600) Integer delayMinutes
) {
}
