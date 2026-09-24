package com.yufesta.domain.cheer.dto.response;

import com.yufesta.domain.cheer.entity.Cheer;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/** 운영자용 응원 메시지 관리 응답. 익명 키 해시는 노출하지 않는다. */
@Builder
public record AdminCheerResponse(
        Long id,
        String content,
        String displayName,
        ModerationStatus moderationStatus,
        int reportCount,
        boolean hidden,
        LocalDateTime createdAt
) {

    public static AdminCheerResponse from(Cheer cheer) {
        return AdminCheerResponse.builder()
                .id(cheer.getId())
                .content(cheer.getContent())
                .displayName(cheer.getDisplayName())
                .moderationStatus(cheer.getModerationStatus())
                .reportCount(cheer.getReportCount())
                .hidden(cheer.isHidden())
                .createdAt(cheer.getCreatedAt())
                .build();
    }
}
