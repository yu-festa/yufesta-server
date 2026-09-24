package com.yufesta.domain.place.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaceCategoryTest {

    @Test
    void 지도_장소_카테고리는_공연장_화장실_배달존만_지원한다() {
        assertThat(PlaceCategory.values())
                .containsExactly(PlaceCategory.STAGE, PlaceCategory.TOILET, PlaceCategory.DELIVERY_ZONE);
    }
}
