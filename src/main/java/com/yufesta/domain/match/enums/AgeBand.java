package com.yufesta.domain.match.enums;

import java.util.Arrays;

/**
 * 나이대. DB에는 ERD 값("19-21" 등)으로 저장되며 변환은 AgeBandConverter가 맡는다.
 * 점수 계산의 "인접"은 선언 순서가 이웃인 경우다
 */
public enum AgeBand {

    A19_21("19-21"),
    A22_24("22-24"),
    A25_27("25-27"),
    A28_PLUS("28+");

    private final String value;

    AgeBand(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isAdjacentTo(AgeBand other) {
        return Math.abs(ordinal() - other.ordinal()) == 1;
    }

    public static AgeBand fromValue(String value) {
        return Arrays.stream(values())
                .filter(band -> band.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 나이대: " + value));
    }
}
