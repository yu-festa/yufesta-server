package com.yufesta.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.exception.error.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void 존재하지_않는_정적_리소스는_500이_아닌_404를_반환한다() {
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/", "")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void Validated_메서드_제약_위반은_400과_마지막_경로_마디를_필드명으로_준다() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ConstraintViolationException exception = new ConstraintViolationException(
                validator.validate(new Sample("x"))
        );

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleConstraintViolationException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("INVALID_INPUT_VALUE");
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("nickname");
    }

    @Test
    void 업로드_크기_초과는_413_PHOTO_TOO_LARGE다() {
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(10L * 1024 * 1024)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody().code()).isEqualTo("PHOTO_TOO_LARGE");
    }

    private record Sample(@Size(min = 2) String nickname) {
    }
}
