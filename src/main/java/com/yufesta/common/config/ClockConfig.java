package com.yufesta.common.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * 서버의 "지금"을 한 곳에서 정한다. 서비스는 이 Clock을 주입받고, JPA Auditing도 같은 Clock으로 시각을 찍는다.
 * 테스트는 Clock.fixed(...)로 바꿔 끼운다
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    // JpaConfig의 @EnableJpaAuditing(dateTimeProviderRef)가 이 빈 이름을 참조한다
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
