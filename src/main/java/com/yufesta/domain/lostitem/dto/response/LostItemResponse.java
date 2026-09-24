package com.yufesta.domain.lostitem.dto.response;

import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.enums.LostItemStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/** 공개 분실물 게시글 응답 */
@Builder
public record LostItemResponse(
        Long id,
        LostItemKind kind,
        String description,
        String placeText,
        LocalDateTime occurredAt,
        LostItemStatus status,
        String displayName,
        LocalDateTime createdAt
) {

    public static LostItemResponse from(LostItem lostItem) {
        return LostItemResponse.builder()
                .id(lostItem.getId())
                .kind(lostItem.getKind())
                .description(lostItem.getDescription())
                .placeText(lostItem.getPlaceText())
                .occurredAt(lostItem.getOccurredAt())
                .status(lostItem.getStatus())
                .displayName(lostItem.getDisplayName())
                .createdAt(lostItem.getCreatedAt())
                .build();
    }
}
