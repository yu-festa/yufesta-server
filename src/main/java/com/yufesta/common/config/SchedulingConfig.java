package com.yufesta.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄 작업 활성화. 개별 작업(RoundScheduler)은 app.scheduler.enabled로 켜고 끈다(test 프로필은 false)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
