package com.yufesta.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.cheer.entity.Cheer;
import com.yufesta.domain.cheer.repository.CheerRepository;
import com.yufesta.domain.lostitem.comment.entity.LostItemComment;
import com.yufesta.domain.lostitem.comment.repository.LostItemCommentRepository;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.report.dto.response.AdminContentReportResponse;
import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.repository.ContentReportRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
    private LostItemRepository lostItemRepository;

    @Autowired
    private LostItemCommentRepository lostItemCommentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void cleanUp() {
        contentReportRepository.deleteAll();
        lostItemCommentRepository.deleteAll();
        lostItemRepository.deleteAll();
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

    @Test
    @Transactional
    void 대상_유형별_신고가_섞여도_목록_조회는_최대_4개_SELECT로_끝난다() {
        User reporter = userRepository.save(user("mixed-target-reporter"));
        Cheer firstCheer = cheerRepository.save(cheer("안전한 축제 열려요!", "신난 수달"));
        Cheer secondCheer = cheerRepository.save(cheer("오늘도 재밌네요!", "조용한 펭귄"));
        LostItem firstLostItem = lostItemRepository.save(lostItem(reporter, "검은 지갑"));
        LostItem secondLostItem = lostItemRepository.save(lostItem(reporter, "하늘색 모자"));
        LostItemComment firstComment = lostItemCommentRepository.save(comment(reporter, firstLostItem, "습득함을 보았어요."));
        LostItemComment secondComment = lostItemCommentRepository.save(comment(reporter, secondLostItem, "안내센터에 물어보세요."));
        firstCheer.hide();
        secondLostItem.hide();
        firstComment.hide();
        contentReportRepository.saveAll(List.of(
                report(reporter, ContentTargetType.CHEER, firstCheer.getId()),
                report(reporter, ContentTargetType.CHEER, secondCheer.getId()),
                report(reporter, ContentTargetType.LOST_ITEM, firstLostItem.getId()),
                report(reporter, ContentTargetType.LOST_ITEM, secondLostItem.getId()),
                report(reporter, ContentTargetType.LOST_ITEM_COMMENT, firstComment.getId()),
                report(reporter, ContentTargetType.LOST_ITEM_COMMENT, secondComment.getId())
        ));
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<AdminContentReportResponse> responses = contentReportService.getReports(null, null, 0, 50);

        assertThat(responses).hasSize(6);
        assertThat(responses.stream().filter(AdminContentReportResponse::targetHidden)).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    private static User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .role(UserRole.USER)
                .loginAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }

    private static Cheer cheer(String content, String displayName) {
        return Cheer.builder()
                .content(content)
                .displayName(displayName)
                .writerKeyHash("writer-" + displayName)
                .build();
    }

    private static LostItem lostItem(User author, String description) {
        return LostItem.builder()
                .kind(LostItemKind.LOST)
                .description(description)
                .placeText("천마아트관")
                .occurredAt(LocalDateTime.of(2026, 10, 2, 13, 0))
                .displayName("알록달록")
                .author(author)
                .build();
    }

    private static LostItemComment comment(User author, LostItem lostItem, String content) {
        return LostItemComment.builder()
                .lostItem(lostItem)
                .author(author)
                .content(content)
                .displayName("고양이대")
                .build();
    }

    private static ContentReport report(User reporter, ContentTargetType targetType, Long targetId) {
        return ContentReport.builder()
                .targetType(targetType)
                .targetId(targetId)
                .reporter(reporter)
                .reason("부적절한 내용")
                .build();
    }
}
