package com.yufesta.domain.cheer.dto.response;

import com.yufesta.domain.cheer.entity.Cheer;
import java.time.LocalDateTime;
import lombok.Builder;

/** 공개 응원 메시지 응답 */
@Builder
public record CheerResponse(
        Long id,
        String content,
        String displayName,
        boolean mine,
        LocalDateTime createdAt
) {

    public static CheerResponse from(Cheer cheer, String myWriterKeyHash) {
        return CheerResponse.builder()
                .id(cheer.getId())
                .content(cheer.getContent())
                .displayName(cheer.getDisplayName())
                .mine(myWriterKeyHash != null && myWriterKeyHash.equals(cheer.getWriterKeyHash()))
                .createdAt(cheer.getCreatedAt())
                .build();
    }
}
