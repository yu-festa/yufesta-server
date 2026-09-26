package com.yufesta.domain.lostitem.dto.response;

import com.yufesta.domain.lostitem.entity.LostItemImage;
import lombok.Builder;

/** 공개 분실물 게시글에 노출하는 이미지 주소 */
@Builder
public record LostItemImageResponse(
        Long id,
        String imageUrl,
        String thumbnailUrl
) {

    public static LostItemImageResponse from(LostItemImage image) {
        return LostItemImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .thumbnailUrl(image.getThumbnailUrl())
                .build();
    }
}
