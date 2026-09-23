package com.yufesta.domain.match.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

/**
 * 설정으로 켰을 때 스케줄 작업이 실제로 등록되는지 확인한다(플레이스홀더·Duration 파싱·@EnableScheduling 배선).
 * test 프로필 기본값은 false라 다른 컨텍스트 테스트에서는 돌지 않는다
 */
@SpringBootTest(properties = {"app.scheduler.enabled=true", "app.scheduler.round-tick=PT1H"})
@ActiveProfiles("test")
class RoundSchedulerWiringTest {

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledTasks;

    @Test
    void 켜면_tick이_스케줄_작업으로_등록된다() {
        assertThat(scheduledTasks.getScheduledTasks())
                .anyMatch(task -> task.toString().contains("RoundScheduler.tick"));
    }
}
