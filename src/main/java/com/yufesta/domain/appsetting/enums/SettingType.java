package com.yufesta.domain.appsetting.enums;

/**
 * 설정값의 타입. 읽을 때 파싱 방식을 정하고, 운영자 변경 API에서 입력 검증에 쓴다
 */
public enum SettingType {

    INT,
    DECIMAL,
    BOOLEAN,
    STRING,
    LIST
}
