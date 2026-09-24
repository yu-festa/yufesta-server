package com.yufesta.domain.lostitem.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/** 안내소가 보관 중인 습득물 등록 요청 */
@Builder
public record CreateOfficialLostItemRequest(
        @Schema(description = "물품 설명", example = "검은색 카드지갑") @NotBlank @Size(max = 100) String description,
        @Schema(description = "습득 장소", example = "중앙도서관 앞") @NotBlank @Size(max = 50) String placeText,
        @Schema(description = "습득 시각", example = "2026-10-02T14:30:00") LocalDateTime occurredAt
) {
}
