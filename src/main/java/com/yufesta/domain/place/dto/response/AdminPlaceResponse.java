package com.yufesta.domain.place.dto.response;

import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import java.math.BigDecimal;
import lombok.Builder;

/** 운영자 장소 응답 */
@Builder
public record AdminPlaceResponse(
        Long id,
        String name,
        PlaceCategory category,
        BigDecimal latitude,
        BigDecimal longitude,
        String description,
        String building,
        String floor,
        int sortOrder,
        boolean active
) {

    public static AdminPlaceResponse from(Place place) {
        return new AdminPlaceResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getLatitude(),
                place.getLongitude(),
                place.getDescription(),
                place.getBuilding(),
                place.getFloor(),
                place.getSortOrder(),
                place.isActive()
        );
    }
}
