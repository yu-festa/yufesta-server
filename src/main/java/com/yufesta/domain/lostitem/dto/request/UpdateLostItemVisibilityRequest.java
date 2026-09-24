package com.yufesta.domain.lostitem.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/** 운영자의 분실물 게시글 숨김 또는 복구 요청 */
@Builder
public record UpdateLostItemVisibilityRequest(
        @Schema(description = "숨김 여부", example = "true") @NotNull Boolean hidden
) {
}
