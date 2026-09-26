package com.yufesta.domain.lostitem.comment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 운영자의 분실물 댓글 숨김·복구 요청 */
public record UpdateLostItemCommentVisibilityRequest(
        @Schema(description = "숨김 여부", example = "true") @NotNull Boolean hidden
) {
}
