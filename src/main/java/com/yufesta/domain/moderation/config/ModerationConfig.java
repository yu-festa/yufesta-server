package com.yufesta.domain.moderation.config;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 외부 모더레이션 호출에만 쓰는 짧은 연결 타임아웃 HTTP 클라이언트 설정. */
@Configuration
@EnableConfigurationProperties(OpenAiModerationProperties.class)
public class ModerationConfig {

    @Bean
    public HttpClient openAiModerationHttpClient(OpenAiModerationProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
    }
}
