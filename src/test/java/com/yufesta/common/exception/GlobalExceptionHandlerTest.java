package com.yufesta.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.exception.error.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
}
