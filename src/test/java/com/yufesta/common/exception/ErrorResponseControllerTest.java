package com.yufesta.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.exception.error.ErrorResponse;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ErrorResponseControllerTest {

    private final ErrorResponseController controller = new ErrorResponseController();

    @Test
    void 필터에서_난_예외는_500_ErrorResponse로_바꾼다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("db down"));

        ResponseEntity<ErrorResponse> response = controller.handleError(request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("db down");
    }

    @Test
    void 알려진_상태_코드는_대응하는_ErrorCode로_바꾼다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);

        ResponseEntity<ErrorResponse> response = controller.handleError(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void 상태_코드가_없으면_500으로_본다() {
        ResponseEntity<ErrorResponse> response = controller.handleError(new MockHttpServletRequest("GET", "/error"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
