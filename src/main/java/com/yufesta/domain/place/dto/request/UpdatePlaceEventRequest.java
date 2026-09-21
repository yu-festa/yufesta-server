package com.yufesta.domain.place.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 장소 이벤트 수정 요청 */
@Builder
public record UpdatePlaceEventRequest(
        @Schema(description = "이벤트명", example = "타로 동아리 별자리")
        @NotBlank
        @Size(max = 50)
        String name,

        @Schema(description = "진행 시간 표기", example = "16:00 - 21:00")
        @NotBlank
        @Size(max = 30)
        String timeText,

        @Schema(description = "표시 순서", example = "1")
        @NotNull
        Integer sortOrder
) {
}
