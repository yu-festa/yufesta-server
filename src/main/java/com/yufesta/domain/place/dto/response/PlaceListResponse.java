package com.yufesta.domain.place.dto.response;

import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import java.math.BigDecimal;
import lombok.Builder;

/**
 * 지도 핀 목록 응답
 */
@Builder
public record PlaceListResponse(
        Long id,
        String name,
        PlaceCategory category,
        BigDecimal latitude,
        BigDecimal longitude
) {

    public static PlaceListResponse from(Place place) {
        return new PlaceListResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                place.getLatitude(),
                place.getLongitude()
        );
    }
}
