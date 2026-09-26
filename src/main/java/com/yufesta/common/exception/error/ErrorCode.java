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

    // 이미지
    IMAGE_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "IMAGE_UNSUPPORTED_TYPE", "jpeg 또는 png 이미지만 올릴 수 있습니다."),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "IMAGE_INVALID", "이미지 파일을 읽을 수 없습니다."),

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다."),

    // 설정 (운영 설정 오류라 사용자 잘못이 아니므로 500)
    APP_SETTING_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "APP_SETTING_NOT_FOUND", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
    APP_SETTING_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "APP_SETTING_INVALID", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // 서비스 오픈 알림
    OPEN_NOTIFICATION_CLOSED(HttpStatus.CONFLICT, "OPEN_NOTIFICATION_CLOSED", "서비스 오픈 알림 신청 기간이 지났어요."),
    OPEN_NOTIFICATION_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "OPEN_NOTIFICATION_NOT_CONFIGURED", "알림 서비스를 준비 중이에요. 잠시 후 다시 시도해 주세요."),
    OPEN_NOTIFICATION_TEST_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "OPEN_NOTIFICATION_TEST_DELIVERY_FAILED", "테스트 푸시 발송에 실패했어요. 브라우저 구독 상태를 확인해 주세요."),

    // 지도
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "장소를 찾을 수 없습니다."),
    PLACE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_EVENT_NOT_FOUND", "장소 이벤트를 찾을 수 없습니다."),

    // 타임테이블
    TIMETABLE_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "TIMETABLE_SLOT_NOT_FOUND", "공연을 찾을 수 없습니다."),
    TIMETABLE_INVALID_TIME(HttpStatus.BAD_REQUEST, "TIMETABLE_INVALID_TIME", "종료 시각은 시작 시각보다 뒤여야 합니다."),
    TIMETABLE_STAGE_INVALID(HttpStatus.BAD_REQUEST, "TIMETABLE_STAGE_INVALID", "무대로 지정할 수 없는 장소입니다."),
    TIMETABLE_ORDER_INVALID(HttpStatus.BAD_REQUEST, "TIMETABLE_ORDER_INVALID", "순서 목록에 모든 공연이 한 번씩 있어야 합니다."),

    // 라인업
    CLUB_NOT_FOUND(HttpStatus.NOT_FOUND, "CLUB_NOT_FOUND", "동아리를 찾을 수 없습니다."),

    // 공지
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지를 찾을 수 없습니다."),

    // 분실물
    LOST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "LOST_ITEM_NOT_FOUND", "분실물 게시글을 찾을 수 없습니다."),
    LOST_ITEM_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "LOST_ITEM_IMAGE_NOT_FOUND", "분실물 이미지를 찾을 수 없습니다."),
    LOST_ITEM_IMAGE_ALREADY_EXISTS(HttpStatus.CONFLICT, "LOST_ITEM_IMAGE_ALREADY_EXISTS", "분실물 게시글에는 이미지 한 장만 등록할 수 있습니다."),
    LOST_ITEM_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "LOST_ITEM_COMMENT_NOT_FOUND", "분실물 댓글을 찾을 수 없습니다."),
    LOST_ITEM_COMMENT_REPLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "LOST_ITEM_COMMENT_REPLY_NOT_ALLOWED", "답글에는 다시 답글을 작성할 수 없습니다."),

    // 콘텐츠 신고
    CONTENT_REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_REPORT_TARGET_NOT_FOUND", "신고할 콘텐츠를 찾을 수 없습니다."),
    CONTENT_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_REPORT_NOT_FOUND", "콘텐츠 신고 내역을 찾을 수 없습니다."),
    CONTENT_NOT_REPORTABLE(HttpStatus.BAD_REQUEST, "CONTENT_NOT_REPORTABLE", "신고할 수 없는 콘텐츠입니다."),
    CONTENT_REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "CONTENT_REPORT_ALREADY_EXISTS", "이미 신고한 콘텐츠입니다."),
    CHEER_NOT_FOUND(HttpStatus.NOT_FOUND, "CHEER_NOT_FOUND", "응원 메시지를 찾을 수 없습니다."),

    // 인스타팅
    MATCH_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "MATCH_ROUND_NOT_FOUND", "회차를 찾을 수 없습니다."),
    MATCH_ROUND_NOT_OPEN(HttpStatus.CONFLICT, "MATCH_ROUND_NOT_OPEN", "지금은 신청을 받지 않는 시간이에요."),
    MATCH_ROUND_INVALID_STATUS(HttpStatus.CONFLICT, "MATCH_ROUND_INVALID_STATUS", "현재 회차 상태에서는 할 수 없는 작업입니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "신청 내역을 찾을 수 없습니다."),
    APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS", "이미 이번 회차에 신청했어요."),
    APPLICATION_INSTAGRAM_DUPLICATE(HttpStatus.CONFLICT, "APPLICATION_INSTAGRAM_DUPLICATE", "이미 등록된 인스타그램 아이디예요."),
    APPLICATION_INVALID_TAG(HttpStatus.BAD_REQUEST, "APPLICATION_INVALID_TAG", "선택할 수 없는 태그예요."),
    APPLICATION_SLOT_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "APPLICATION_SLOT_NOT_SELECTABLE", "이번 회차에서는 고를 수 없는 공연이에요."),
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
