package com.yufesta.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영처럼 쿠키 domain을 지정했을 때 XSRF-TOKEN 쿠키가 실제 Tomcat을 통과하는지 확인.
 * MockMvc는 Tomcat의 쿠키 검증(Rfc6265CookieProcessor)을 거치지 않아 선행 '.'으로 인한 운영 500을 잡지 못했다.
 * 그래서 이 테스트만 서버를 띄운다
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.auth.cookie.domain=.yufesta.com")
class CsrfCookieDomainTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void 선행_점이_있는_domain_설정으로도_CSRF_쿠키를_발급한다() throws Exception {
        HttpResponse<Void> response = client.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding()
        );

        assertThat(response.statusCode()).isEqualTo(204);
        List<String> cookies = response.headers().allValues("Set-Cookie");
        assertThat(cookies).anySatisfy(cookie -> {
            assertThat(cookie).startsWith("XSRF-TOKEN=");
            assertThat(cookie).contains("Domain=yufesta.com");
            assertThat(cookie).doesNotContain("HttpOnly");
        });
        assertThat(cookies).noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
    }

    @Test
    void CSRF_쿠키_없는_쓰기_요청은_500이_아니라_403이다() throws Exception {
        HttpResponse<Void> response = client.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/logout")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding()
        );

        assertThat(response.statusCode()).isEqualTo(403);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
