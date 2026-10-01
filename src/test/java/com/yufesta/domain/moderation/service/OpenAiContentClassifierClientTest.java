package com.yufesta.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.yufesta.domain.moderation.config.OpenAiContentProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiContentClassifierClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 구조화된_차단_판정을_응답으로_변환한다() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(
                200,
                completionResponse("{\"decision\":\"BLOCK\",\"category\":\"THREAT\",\"confidence\":0.93}"),
                requestBody
        );

        ContentClassification result = client().classify("판정할 문장");

        assertThat(result).isEqualTo(new ContentClassification(
                ContentClassification.Decision.BLOCK,
                ContentClassification.Category.THREAT,
                new BigDecimal("0.93")
        ));
        JsonNode request = objectMapper.readTree(requestBody.get());
        assertThat(request.path("model").asString()).isEqualTo("gpt-4.1-mini");
        assertThat(request.path("messages").path(1).path("content").asString()).isEqualTo("판정할 문장");
        assertThat(request.path("response_format").path("type").asString()).isEqualTo("json_schema");
    }

    @Test
    void 구조화된_허용_판정을_응답으로_변환한다() throws IOException {
        startServer(
                200,
                completionResponse("{\"decision\":\"ALLOW\",\"category\":\"NONE\",\"confidence\":0.88}"),
                new AtomicReference<>()
        );

        assertThat(client().classify("축제 파이팅!")).isEqualTo(new ContentClassification(
                ContentClassification.Decision.ALLOW,
                ContentClassification.Category.NONE,
                new BigDecimal("0.88")
        ));
    }

    @Test
    void 구조화_판정_응답이_올바르지_않으면_실패로_처리한다() throws IOException {
        startServer(200, completionResponse("{\"decision\":\"UNKNOWN\"}"), new AtomicReference<>());

        assertThatThrownBy(() -> client().classify("판정할 문장"))
                .isInstanceOf(OpenAiModerationException.class)
                .hasMessageContaining("올바르지 않습니다");
    }

    @Test
    void OpenAI가_오류를_반환하면_실패로_처리한다() throws IOException {
        startServer(429, "{}", new AtomicReference<>());

        assertThatThrownBy(() -> client().classify("판정할 문장"))
                .isInstanceOf(OpenAiModerationException.class)
                .hasMessageContaining("HTTP 429");
    }

    private OpenAiContentClassifierClient client() {
        OpenAiContentProperties properties = new OpenAiContentProperties(
                "test-api-key",
                URI.create("http://localhost:" + server.getAddress().getPort()),
                Duration.ofSeconds(1),
                "gpt-4.1-mini"
        );
        return new OpenAiContentClassifierClient(HttpClient.newHttpClient(), objectMapper, properties);
    }

    private String completionResponse(String content) {
        return objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))
        ));
    }

    private void startServer(int status, String responseBody, AtomicReference<String> requestBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }
}
