package com.yufesta.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.appsetting.entity.AppSetting;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.cheer.entity.Cheer;
import com.yufesta.domain.cheer.repository.CheerRepository;
import com.yufesta.domain.report.dto.request.CreateContentReportRequest;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.repository.ContentReportRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 실제 DB 잠금으로 같은 콘텐츠의 동시 신고가 누락되지 않는지 검증 */
@SpringBootTest
@ActiveProfiles("test")
class ContentReportConcurrencyTest {

    @Autowired
    private ContentReportService contentReportService;

    @Autowired
    private ContentReportRepository contentReportRepository;

    @Autowired
    private CheerRepository cheerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private AppSettingReader appSettingReader;

    @AfterEach
    void cleanUp() {
        contentReportRepository.deleteAll();
        cheerRepository.deleteAll();
        userRepository.deleteAll();
        appSettingRepository.deleteAll();
        appSettingReader.invalidate();
    }

    @Test
    void 서로_다른_두_사용자가_같은_응원_메시지를_동시에_신고하면_카운트와_자동_숨김이_정확하다() throws Exception {
        appSettingRepository.saveAndFlush(new AppSetting(SettingKey.REPORT_HIDE_THRESHOLD.key(), "2", "콘텐츠 신고 자동 숨김 기준"));
        appSettingReader.invalidate();
        User firstReporter = userRepository.save(user("concurrent-first"));
        User secondReporter = userRepository.save(user("concurrent-second"));
        Cheer cheer = cheerRepository.saveAndFlush(Cheer.builder()
                .content("축제 최고예요!")
                .displayName("신난 수달")
                .writerKeyHash("writer-hash")
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> reportAfterStart(ready, start, firstReporter.getId(), cheer.getId())),
                    executor.submit(() -> reportAfterStart(ready, start, secondReporter.getId(), cheer.getId()))
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Cheer reportedCheer = cheerRepository.findById(cheer.getId()).orElseThrow();
        assertThat(contentReportRepository.count()).isEqualTo(2);
        assertThat(reportedCheer.getReportCount()).isEqualTo(2);
        assertThat(reportedCheer.isHidden()).isTrue();
    }

    private void reportAfterStart(CountDownLatch ready, CountDownLatch start, Long userId, Long cheerId) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 신고 시작 신호를 받지 못했습니다.");
            }
            contentReportService.create(
                    userId,
                    new CreateContentReportRequest(ContentTargetType.CHEER, cheerId, "부적절한 내용")
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 신고 테스트가 중단되었습니다.", exception);
        }
    }

    private static User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .role(UserRole.USER)
                .loginAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }
}
