package com.yufesta.domain.lostitem.dto.request;

import com.yufesta.domain.lostitem.enums.LostItemKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/** 분실물 게시글 작성 요청 */
@Builder
public record CreateLostItemRequest(
        @Schema(description = "구분", example = "FOUND") @NotNull LostItemKind kind,
        @Schema(description = "물품 설명", example = "검은색 카드지갑") @NotBlank @Size(max = 100) String description,
        @Schema(description = "발견 또는 분실 장소", example = "중앙도서관 앞") @NotBlank @Size(max = 50) String placeText,
        @Schema(description = "발견 또는 분실 시각", example = "2026-10-02T14:30:00") LocalDateTime occurredAt
) {
}
