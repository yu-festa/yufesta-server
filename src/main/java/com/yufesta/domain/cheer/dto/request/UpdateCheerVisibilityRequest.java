package com.yufesta.domain.cheer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/** 운영자의 응원 메시지 숨김 또는 복구 요청 */
@Builder
public record UpdateCheerVisibilityRequest(
        @Schema(description = "숨김 여부", example = "true") @NotNull Boolean hidden
) {
}
