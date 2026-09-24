package com.yufesta.common.exception.error;

import org.springframework.http.HttpStatus;

/**
 * 클라이언트에 반환할 HTTP 상태, 오류 코드, 안내 문구를 정의
 */
public enum ErrorCode {

    // 서버 오류
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // 요청 오류
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_INPUT_VALUE", "입력값이 올바르지 않습니다."),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "요청 본문을 읽을 수 없습니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER", "필수 요청값이 누락되었습니다."),
    INVALID_PARAMETER_TYPE(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_TYPE", "요청값 형식이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
    PHOTO_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "PHOTO_TOO_LARGE", "파일 크기가 너무 큽니다."),

    // 인증 및 인가 오류
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다."),

    // 설정 (운영 설정 오류라 사용자 잘못이 아니므로 500)
    APP_SETTING_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "APP_SETTING_NOT_FOUND", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    APP_SETTING_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "APP_SETTING_INVALID", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // 지도
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다."),
    PLACE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_EVENT_NOT_FOUND", "장소 이벤트를 찾을 수 없습니다."),

    // 공지
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다."),

    // 인스타팅
    MATCH_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_ROUND_NOT_FOUND", "회차를 찾을 수 없습니다."),
    MATCH_ROUND_NOT_OPEN(HttpStatus.CONFLICT, "MATCH_ROUND_NOT_OPEN", "지금은 신청을 받지 않는 시간이에요."),
    MATCH_ROUND_INVALID_STATUS(HttpStatus.CONFLICT, "MATCH_ROUND_INVALID_STATUS", "현재 회차 상태에서는 할 수 없는 작업입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "신청 내역을 찾을 수 없습니다."),
    APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS", "이미 이번 회차에 신청했어요."),
    APPLICATION_INSTAGRAM_DUPLICATE(HttpStatus.CONFLICT, "APPLICATION_INSTAGRAM_DUPLICATE", "이미 등록된 인스타그램 아이디예요."),
    APPLICATION_INVALID_TAG(HttpStatus.BAD_REQUEST, "APPLICATION_INVALID_TAG", "선택할 수 없는 태그예요."),
    USER_MATCHING_BLOCKED(HttpStatus.FORBIDDEN, "USER_MATCHING_BLOCKED", "인스타팅 참여가 제한된 계정이에요."),
    MATCH_RESULT_NOT_PUBLISHED(HttpStatus.CONFLICT, "MATCH_RESULT_NOT_PUBLISHED", "아직 결과 발표 전이에요."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "매칭 결과를 찾을 수 없습니다."),
    BLOCK_ALREADY_EXISTS(HttpStatus.CONFLICT, "BLOCK_ALREADY_EXISTS", "이미 신고한 상대예요."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOCK_NOT_FOUND", "신고 내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
