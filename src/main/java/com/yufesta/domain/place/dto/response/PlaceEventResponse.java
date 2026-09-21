package com.yufesta.domain.place.dto.response;

import com.yufesta.domain.place.entity.PlaceEvent;
import lombok.Builder;

/**
 * 장소 진행 이벤트 응답
 */
@Builder
public record PlaceEventResponse(
        Long id,
        String name,
        String timeText,
        int sortOrder
) {

    public static PlaceEventResponse from(PlaceEvent placeEvent) {
        return new PlaceEventResponse(
                placeEvent.getId(),
                placeEvent.getName(),
                placeEvent.getTimeText(),
                placeEvent.getSortOrder()
        );
    }
}
