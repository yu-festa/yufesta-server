package com.yufesta.common.response;

import org.springframework.http.HttpStatus;

/**
 * 모든 API 성공 응답에 사용하는 공통 응답 본문
 */
public record ApiResponse<T>(
        int status,
        String message,
        T data
) {

    // 기본 성공 메시지와 함께 데이터를 반환
    public static <T> ApiResponse<T> success(T data) {
        return success("요청에 성공했습니다.", data);
    }

    // 지정한 성공 메시지와 함께 데이터를 반환
    public static <T> ApiResponse<T> success(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    // 데이터가 없는 성공 응답을 반환
    public static ApiResponse<Void> success() {
        return success("요청에 성공했습니다.");
    }

    // 지정한 성공 메시지와 함께 데이터 없는 응답을 반환
    public static ApiResponse<Void> success(String message) {
        return of(HttpStatus.OK, message, null);
    }

    // HTTP 상태 코드, 메시지, 데이터를 가진 성공 응답을 생성
    public static <T> ApiResponse<T> of(HttpStatus status, String message, T data) {
        return new ApiResponse<>(status.value(), message, data);
    }
}
