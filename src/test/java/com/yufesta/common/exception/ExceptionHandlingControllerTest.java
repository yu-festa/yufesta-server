package com.yufesta.common.exception;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.TestEndpointController;
import com.yufesta.support.WithMockLoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * 잘못된 요청이 컨트롤러까지 왔을 때 GlobalExceptionHandler가 4xx ErrorResponse로 바꾸는지 MVC 경로로 확인
 */
@WebMvcTest(controllers = TestEndpointController.class)
@Import(TestEndpointController.class)
class ExceptionHandlingControllerTest extends ControllerTestSupport {

    @Test
    void 파라미터_제약_위반은_400_INVALID_INPUT_VALUE와_필드명을_준다() throws Exception {
        mockMvc.perform(get("/api/v1/validated").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void 필수_파라미터_누락은_400_MISSING_REQUEST_PARAMETER다() throws Exception {
        mockMvc.perform(get("/api/v1/validated"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void 파라미터_타입_불일치는_400_INVALID_PARAMETER_TYPE이다() throws Exception {
        mockMvc.perform(get("/api/v1/validated").param("size", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @WithMockLoginUser
    void 깨진_JSON_본문은_400_INVALID_REQUEST_BODY다() throws Exception {
        mockMvc.perform(post("/api/v1/echo").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    @WithMockLoginUser
    void 지원하지_않는_메서드는_405_METHOD_NOT_ALLOWED다() throws Exception {
        mockMvc.perform(put("/api/v1/ping").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
