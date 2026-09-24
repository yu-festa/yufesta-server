package com.yufesta.domain.club.dto.response;

import com.yufesta.domain.club.entity.Club;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 운영자용 동아리 응답. 공연은 붙이지 않고 저장된 컬럼만 */
@Builder
public record AdminClubResponse(
        Long id,
        String name,
        String intro,
        String genre,
        String signatureSong,
        String instagramUrl,
        String photoUrl,
        int sortOrder,
        @Schema(description = "등록한 운영자 ID. 초기 데이터는 null") Long createdById
) {

    public static AdminClubResponse from(Club club) {
        return AdminClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .intro(club.getIntro())
                .genre(club.getGenre())
                .signatureSong(club.getSignatureSong())
                .instagramUrl(club.getInstagramUrl())
                .photoUrl(club.getPhotoUrl())
                .sortOrder(club.getSortOrder())
                .createdById(club.getCreatedBy() == null ? null : club.getCreatedBy().getId())
                .build();
    }
}
