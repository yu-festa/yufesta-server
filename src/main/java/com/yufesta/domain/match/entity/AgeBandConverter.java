package com.yufesta.domain.match.entity;

import com.yufesta.domain.match.enums.AgeBand;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * AgeBand ↔ ERD 저장값("19-21", "28+"). enum 이름은 하이픈·플러스를 가질 수 없어 변환기가 필요하다
 */
@Converter
public class AgeBandConverter implements AttributeConverter<AgeBand, String> {

    @Override
    public String convertToDatabaseColumn(AgeBand ageBand) {
        return ageBand == null ? null : ageBand.value();
    }

    @Override
    public AgeBand convertToEntityAttribute(String value) {
        return value == null ? null : AgeBand.fromValue(value);
    }
}
