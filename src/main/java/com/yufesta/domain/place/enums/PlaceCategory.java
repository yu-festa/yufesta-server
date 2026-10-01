package com.yufesta.domain.place.enums;

/**
 * 지도에서 장소를 구분하는 카테고리.
 * BOOTH는 2026-10-01 총동연 요청(커피차·푸드트럭)으로 추가했다. 컬럼이 VARCHAR(20)이라 마이그레이션 없이 값만 늘었다
 */
public enum PlaceCategory {
    STAGE,
    TOILET,
    DELIVERY_ZONE,
    BOOTH
}
