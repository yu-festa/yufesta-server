package com.yufesta.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/** 진행 중 수동 지정 요청 */
@Builder
public record LiveSlotRequest(
        @Schema(description = "true면 이 공연만 진행 중으로 지정(다른 공연 지정은 해제), false면 이 공연의 지정 해제", example = "true")
        @NotNull Boolean live
) {
}
