package com.yufesta.domain.timetable.dto.response;

import com.yufesta.domain.club.entity.Club;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 공연의 출연 동아리. id로 라인업 카드로 이동한다(FR-TT-06). 카드 내용은 라인업 API가 준다 */
@Builder
public record TimetableClubResponse(
        @Schema(description = "동아리 ID", example = "3") Long id,
        @Schema(description = "동아리명", example = "HIPCOM") String name
) {

    public static TimetableClubResponse from(Club club) {
        return TimetableClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .build();
    }
}
