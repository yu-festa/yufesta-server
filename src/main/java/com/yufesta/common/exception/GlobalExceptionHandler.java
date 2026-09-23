package com.yufesta.common.exception;

import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.exception.error.ErrorResponse;
import com.yufesta.common.exception.error.ValidationError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러 예외를 공통 JSON 오류 응답으로 변환. 4xx는 로그를 남기지 않고 5xx만 error로 남긴다
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String REQUIRED_MESSAGE = "필수 값입니다.";
    private static final String TYPE_MISMATCH_MESSAGE = "형식이 올바르지 않습니다.";

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException exception) {
        return response(exception.getErrorCode());
    }

    // @RequestBody record의 Bean Validation 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .toList();
        return response(ErrorCode.INVALID_INPUT_VALUE, errors);
    }

    // @RequestParam·@PathVariable에 붙은 제약(@Min 등) 실패. Spring 6.1+ 컨트롤러 메서드 검증이 던진다
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        List<ValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> toValidationError(result, error)))
                .toList();
        return response(ErrorCode.INVALID_INPUT_VALUE, errors);
    }

    // @Validated 클래스의 메서드 제약 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
                .map(this::toValidationError)
                .toList();
        return response(ErrorCode.INVALID_INPUT_VALUE, errors);
    }

    // JSON 본문이 비었거나 파싱할 수 없음
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return response(ErrorCode.INVALID_REQUEST_BODY);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        return response(
                ErrorCode.MISSING_REQUEST_PARAMETER,
                List.of(new ValidationError(exception.getParameterName(), REQUIRED_MESSAGE))
        );
    }

    // 예: size=abc, 경로 변수에 숫자가 아닌 값
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        return response(
                ErrorCode.INVALID_PARAMETER_TYPE,
                List.of(new ValidationError(exception.getName(), TYPE_MISMATCH_MESSAGE))
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        return response(ErrorCode.PHOTO_TOO_LARGE);
    }

    // 존재하지 않는 API 또는 정적 리소스 요청은 404로 반환
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        return response(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ValidationError toValidationError(FieldError fieldError) {
        return new ValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ValidationError toValidationError(ParameterValidationResult result, MessageSourceResolvable error) {
        return new ValidationError(result.getMethodParameter().getParameterName(), error.getDefaultMessage());
    }

    // propertyPath는 "메서드명.파라미터명" 형태라 마지막 마디만 필드명으로 쓴다
    private ValidationError toValidationError(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String field = path.substring(path.lastIndexOf('.') + 1);
        return new ValidationError(field, violation.getMessage());
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
    }

    private ResponseEntity<ErrorResponse> response(ErrorCode errorCode, List<ValidationError> errors) {
        return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode, errors));
    }
}
