package com.yufesta.common.exception.error;

import java.util.List;

/**
 * 모든 API 오류에 사용하는 공통 응답 본문
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        List<ValidationError> errors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.status().value(),
                errorCode.code(),
                errorCode.message(),
                List.of()
        );
    }

    public static ErrorResponse of(ErrorCode errorCode, List<ValidationError> errors) {
        return new ErrorResponse(
                errorCode.status().value(),
                errorCode.code(),
                errorCode.message(),
                List.copyOf(errors)
        );
    }
}
