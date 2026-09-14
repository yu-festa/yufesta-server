package com.yufesta.common.exception.error;

/**
 * 요청값 검증 실패 시 문제가 된 필드와 메시지를 전달합니다.
 */
public record ValidationError(String field, String message) {
}
