package com.yufesta.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.appsetting.entity.AppSetting;
import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * created_at·updated_at이 시스템 시계가 아니라 Clock 빈으로 찍히는지 확인
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, ClockConfig.class, JpaAuditingClockTest.FixedClockConfig.class})
class JpaAuditingClockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 16, 0);

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Test
    void 생성_수정_시각은_Clock_빈_기준으로_기록된다() {
        AppSetting saved = appSettingRepository.saveAndFlush(new AppSetting("test.key", "1", "테스트"));

        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }

    // ClockConfig의 시스템 Clock 대신 고정 Clock을 우선 주입
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(NOW.atZone(kst).toInstant(), kst);
        }
    }
}
