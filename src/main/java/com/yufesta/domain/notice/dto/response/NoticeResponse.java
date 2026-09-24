package com.yufesta.domain.notice.dto.response;

import com.yufesta.domain.notice.entity.Notice;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record NoticeResponse(
        Long id,
        String title,
        String body,
        boolean banner,
        LocalDateTime createdAt
) {

    public static NoticeResponse from(Notice notice) {
        return NoticeResponse.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .body(notice.getBody())
                .banner(notice.isBanner())
                .createdAt(notice.getCreatedAt())
                .build();
    }
}
