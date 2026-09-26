package com.yufesta.domain.opennotification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 서비스 오픈 Web Push 설정을 환경변수에서 바인딩한다. */
@Configuration
@EnableConfigurationProperties(OpenNotificationProperties.class)
public class OpenNotificationConfig {
}
