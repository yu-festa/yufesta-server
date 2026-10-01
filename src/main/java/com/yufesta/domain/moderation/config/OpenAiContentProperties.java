package com.yufesta.domain.moderation.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OpenAI 콘텐츠 분류 API 접속 정보. 실제 키는 환경변수·SSM에만 둔다. */
@ConfigurationProperties(prefix = "app.moderation.openai")
public record OpenAiContentProperties(String apiKey, URI baseUrl, Duration timeout, String model) {

    private static final URI DEFAULT_BASE_URL = URI.create("https://api.openai.com/v1");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    private static final String DEFAULT_MODEL = "gpt-4.1-mini";

    public OpenAiContentProperties {
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }
}
