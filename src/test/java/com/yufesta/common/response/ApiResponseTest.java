package com.yufesta.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiResponseTest {

    @Test
    void 데이터가_있는_성공_응답을_생성한다() {
        Map<String, Object> data = Map.of("id", 1L, "name", "YU Festa");

        ApiResponse<Map<String, Object>> response = ApiResponse.success("축제 조회에 성공했습니다.", data);

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.message()).isEqualTo("축제 조회에 성공했습니다.");
        assertThat(response.data()).isEqualTo(data);
    }

    @Test
    void 데이터가_없는_성공_응답을_생성한다() {
        ApiResponse<Void> response = ApiResponse.success("로그아웃되었습니다.");

        assertThat(response.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.message()).isEqualTo("로그아웃되었습니다.");
        assertThat(response.data()).isNull();
    }
}
