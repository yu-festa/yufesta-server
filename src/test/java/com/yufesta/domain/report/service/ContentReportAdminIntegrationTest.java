package com.yufesta.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.cheer.entity.Cheer;
import com.yufesta.domain.cheer.repository.CheerRepository;
import com.yufesta.domain.report.dto.response.AdminContentReportResponse;
import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.repository.ContentReportRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 실제 스키마에서 운영자 신고 필터·검토 시각 저장을 검증 */
@SpringBootTest
@ActiveProfiles("test")
class ContentReportAdminIntegrationTest {

    @Autowired
    private ContentReportService contentReportService;

    @Autowired
    private ContentReportRepository contentReportRepository;

    @Autowired
    private CheerRepository cheerRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        contentReportRepository.deleteAll();
        cheerRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 미검토_신고를_대상유형으로_조회하고_검토_처리하면_검토목록으로_이동한다() {
        User reporter = userRepository.save(user("admin-report-user"));
        Cheer cheer = cheerRepository.saveAndFlush(Cheer.builder()
                .content("축제 최고예요!")
                .displayName("신난 수달")
                .writerKeyHash("writer-hash")
                .build());
        ContentReport report = contentReportRepository.saveAndFlush(ContentReport.builder()
                .targetType(ContentTargetType.CHEER)
                .targetId(cheer.getId())
                .reporter(reporter)
                .reason("부적절한 내용")
                .build());

        List<AdminContentReportResponse> pending = contentReportService.getReports(false, ContentTargetType.CHEER, 0, 20);

        assertThat(pending).singleElement()
                .extracting(
                        AdminContentReportResponse::id,
                        AdminContentReportResponse::reporterUserId,
                        AdminContentReportResponse::targetReportCount,
                        AdminContentReportResponse::targetHidden
                )
                .containsExactly(report.getId(), reporter.getId(), 0, false);

        AdminContentReportResponse reviewed = contentReportService.review(report.getId());

        assertThat(reviewed.reviewedAt()).isNotNull();
        assertThat(contentReportService.getReports(false, ContentTargetType.CHEER, 0, 20)).isEmpty();
        assertThat(contentReportService.getReports(true, ContentTargetType.CHEER, 0, 20))
                .extracting(AdminContentReportResponse::id)
                .containsExactly(report.getId());
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
