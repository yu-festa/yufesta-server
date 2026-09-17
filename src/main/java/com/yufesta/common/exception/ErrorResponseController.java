package com.yufesta.common.exception;

import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.exception.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 컨트롤러 밖(필터, 서블릿 컨테이너)에서 난 오류가 /error로 넘어올 때도 공통 ErrorResponse로 응답.
 * Boot 기본 BasicErrorController를 대체한다
 */
@Hidden
@RestController
public class ErrorResponseController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseController.class);

    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        ErrorCode errorCode = toErrorCode(status);

        if (status.is5xxServerError()) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error("필터 처리 중 오류: status={}", status.value(),
                    exception instanceof Throwable throwable ? throwable : null);
        }
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = statusCode instanceof Integer code ? HttpStatus.resolve(code) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private ErrorCode toErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.INVALID_INPUT_VALUE;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ErrorCode.METHOD_NOT_ALLOWED;
            case CONTENT_TOO_LARGE -> ErrorCode.PHOTO_TOO_LARGE;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}
