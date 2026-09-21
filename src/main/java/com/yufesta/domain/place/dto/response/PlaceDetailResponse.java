package com.yufesta.domain.place.dto.response;

import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

/**
 * 장소 상세 응답
 */
@Builder
public record PlaceDetailResponse(
        Long id,
        String name,
        PlaceCategory category,
        BigDecimal latitude,
        BigDecimal longitude,
        String description,
        String building,
        String floor,
        List<PlaceEventResponse> events
) {

    public static PlaceDetailResponse of(Place place, List<PlaceEventResponse> events) {
        return new PlaceDetailResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getLatitude(),
                place.getLongitude(),
                place.getDescription(),
                place.getBuilding(),
                place.getFloor(),
                events
        );
    }
}
