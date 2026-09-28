package com.yufesta.domain.moderation.service;

import com.yufesta.domain.moderation.config.OpenAiModerationProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** OpenAI Moderation API의 점수를 설정 임계값과 비교한다. */
@Component
public class OpenAiModerationClient {

    private static final String MODEL = "omni-moderation-latest";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiModerationProperties properties;

    public OpenAiModerationClient(
            HttpClient openAiModerationHttpClient,
            ObjectMapper objectMapper,
            OpenAiModerationProperties properties
    ) {
        this.httpClient = openAiModerationHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 카테고리 점수 중 하나라도 임계값 이상이면 true를 반환한다. */
    public boolean exceedsThreshold(String content, BigDecimal threshold) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new OpenAiModerationException("OPENAI_API_KEY가 설정되지 않았습니다");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(moderationsUri())
                    .timeout(properties.timeout())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(new ModerationRequest(MODEL, content))
                    ))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OpenAiModerationException("OpenAI Moderation API HTTP " + response.statusCode());
            }
            return hasScoreAtLeast(objectMapper.readTree(response.body()), threshold);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiModerationException("OpenAI Moderation API 호출이 중단됐습니다", exception);
        } catch (IOException exception) {
            throw new OpenAiModerationException("OpenAI Moderation API 호출에 실패했습니다", exception);
        }
    }

    private URI moderationsUri() {
        String baseUrl = properties.baseUrl().toString().replaceAll("/+$", "");
        return URI.create(baseUrl + "/moderations");
    }

    private boolean hasScoreAtLeast(JsonNode response, BigDecimal threshold) {
        JsonNode scores = response.path("results").path(0).path("category_scores");
        if (!scores.isObject()) {
            throw new OpenAiModerationException("OpenAI Moderation API 응답에 category_scores가 없습니다");
        }
        // Jackson 3는 fields() 대신 properties()로 객체 필드를 제공한다.
        Iterator<Map.Entry<String, JsonNode>> fields = scores.properties().iterator();
        while (fields.hasNext()) {
            JsonNode score = fields.next().getValue();
            if (score.isNumber() && BigDecimal.valueOf(score.asDouble()).compareTo(threshold) >= 0) {
                return true;
            }
        }
        return false;
    }

    private record ModerationRequest(String model, String input) {
    }
}
