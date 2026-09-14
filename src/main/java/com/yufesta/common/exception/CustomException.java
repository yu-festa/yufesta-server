package com.yufesta.common.exception;

import com.yufesta.common.exception.error.ErrorCode;
import java.util.Objects;

/**
 * 도메인 규칙 위반을 공통 오류 코드로 전달하는 예외입니다.
 */
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
