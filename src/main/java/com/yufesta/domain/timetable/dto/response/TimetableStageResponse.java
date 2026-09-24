package com.yufesta.domain.timetable.dto.response;

import com.yufesta.domain.place.entity.Place;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 공연 무대. placeId로 지도의 해당 핀으로 이동한다(FR-LU-05) */
@Builder
public record TimetableStageResponse(
        @Schema(description = "무대 장소 ID", example = "1") Long placeId,
        @Schema(description = "무대 이름", example = "중앙 무대") String name
) {

    public static TimetableStageResponse from(Place place) {
        return TimetableStageResponse.builder()
                .placeId(place.getId())
                .name(place.getName())
                .build();
    }
}
