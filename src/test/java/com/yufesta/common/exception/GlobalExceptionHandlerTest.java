package com.yufesta.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.yufesta.common.exception.error.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    void 끊긴_연결에_쓰다_난_예외는_오류로_기록하지_않고_응답도_만들지_않는다(CapturedOutput output) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DisconnectedController())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(get("/disconnected")).andReturn();

        // 공통 처리(Exception)로 흘러가면 500 본문을 쓰려다 다시 실패하고 ERROR와 스택이 남는다
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(500);
        assertThat(output.getAll()).doesNotContain("Unhandled exception");
    }

    @Test
    void 그_밖의_예외는_여전히_500과_오류_로그를_남긴다(CapturedOutput output) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DisconnectedController())
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = mockMvc.perform(get("/broken")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(output.getAll()).contains("Unhandled exception");
    }

    @RestController
    private static class DisconnectedController {

        @GetMapping("/disconnected")
        String disconnected() throws IOException {
            throw new AsyncRequestNotUsableException("Servlet container error notification for disconnected client");
        }

        @GetMapping("/broken")
        String broken() {
            throw new IllegalStateException("진짜 오류");
        }
    }

    private record Sample(@Size(min = 2) String nickname) {
    }
}
