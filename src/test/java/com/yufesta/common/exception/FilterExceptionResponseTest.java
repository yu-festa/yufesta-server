package com.yufesta.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.yufesta.common.security.jwt.JwtTokenProvider;
import com.yufesta.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

/**
 * 필터(JWT 인증)에서 난 DB 예외가 실제 서블릿 컨테이너의 /error를 거쳐 공통 ErrorResponse로 나가는지 확인.
 * MockMvc는 컨테이너 오류 디스패치를 재현하지 못하므로 실제 포트로 띄운다
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FilterExceptionResponseTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void 인증_필터의_DB_장애는_500_ErrorResponse_JSON으로_응답한다() {
        when(userRepository.findById(anyLong())).thenThrow(new DataAccessResourceFailureException("db down"));
        String token = jwtTokenProvider.createAccessToken(1L);

        RestClient.create("http://localhost:" + port)
                .get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.COOKIE, "access_token=" + token)
                .exchange((request, response) -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(500);
                    assertThat(response.getHeaders().getContentType().toString()).contains("application/json");
                    String body = new String(response.getBody().readAllBytes());
                    assertThat(body).contains("\"code\":\"INTERNAL_SERVER_ERROR\"").doesNotContain("db down");
                    return null;
                });
    }
}
