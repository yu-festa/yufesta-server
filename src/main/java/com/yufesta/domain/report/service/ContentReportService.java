package com.yufesta.domain.report.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.cheer.service.CheerService;
import com.yufesta.domain.lostitem.comment.service.LostItemCommentService;
import com.yufesta.domain.lostitem.service.LostItemService;
import com.yufesta.domain.report.dto.request.CreateContentReportRequest;
import com.yufesta.domain.report.dto.response.AdminContentReportResponse;
import com.yufesta.domain.report.dto.response.ContentReportResponse;
import com.yufesta.domain.report.dto.response.ContentTargetStatus;
import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.repository.ContentReportRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 콘텐츠 신고 접수와 누적 자동 숨김을 처리 */
@Service
@Transactional(readOnly = true)
public class ContentReportService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ContentReportRepository contentReportRepository;
    private final UserService userService;
    private final CheerService cheerService;
    private final LostItemService lostItemService;
    private final LostItemCommentService lostItemCommentService;
    private final AppSettingReader appSettingReader;
    private final Clock clock;

    public ContentReportService(
            ContentReportRepository contentReportRepository,
            UserService userService,
            CheerService cheerService,
            LostItemService lostItemService,
            LostItemCommentService lostItemCommentService,
            AppSettingReader appSettingReader,
            Clock clock
    ) {
        this.contentReportRepository = contentReportRepository;
        this.userService = userService;
        this.cheerService = cheerService;
        this.lostItemService = lostItemService;
        this.lostItemCommentService = lostItemCommentService;
        this.appSettingReader = appSettingReader;
        this.clock = clock;
    }

    /**
     * 콘텐츠를 신고한다(FR-LF-04, FR-CH-03).
     * <p>대상 행을 먼저 잠가 같은 콘텐츠의 신고 저장, 카운터 증가, 임계값 자동 숨김을 직렬화한다.
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND, CONTENT_REPORT_TARGET_NOT_FOUND,
     * CONTENT_NOT_REPORTABLE, CONTENT_REPORT_ALREADY_EXISTS
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ContentReportResponse create(Long userId, CreateContentReportRequest request) {
        User reporter = requireUser(userId);
        lockReportableTarget(request.targetType(), request.targetId());
        if (contentReportRepository.existsByTargetTypeAndTargetIdAndReporter_Id(
                request.targetType(), request.targetId(), reporter.getId())) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_ALREADY_EXISTS);
        }

        ContentReport report = ContentReport.builder()
                .targetType(request.targetType())
                .targetId(request.targetId())
                .reporter(reporter)
                .reason(request.reason().trim())
                .build();
        ContentReport saved = saveAndTranslate(report);
        incrementReportCountAndHideIfThreshold(request.targetType(), request.targetId());
        return ContentReportResponse.from(saved);
    }

    /** 운영자용 콘텐츠 신고 목록을 최신순으로 조회한다(FR-ADM-04). */
    public List<AdminContentReportResponse> getReports(
            Boolean reviewed,
            ContentTargetType targetType,
            int page,
            int size
    ) {
        validatePage(page, size);
        Pageable pageable = PageRequest.of(page, size);
        return findPage(reviewed, targetType, pageable).stream()
                .map(report -> AdminContentReportResponse.from(report, getTargetStatus(report)))
                .toList();
    }

    /** 운영자가 콘텐츠 신고를 검토 처리한다. 처리 결과는 현재 ERD에 없으므로 검토 시각만 기록한다. */
    @Transactional
    public AdminContentReportResponse review(Long reportId) {
        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_NOT_FOUND));
        report.review(LocalDateTime.now(clock));
        return AdminContentReportResponse.from(report, getTargetStatus(report));
    }

    private void lockReportableTarget(ContentTargetType targetType, Long targetId) {
        switch (targetType) {
            case CHEER -> cheerService.lockReportableForReport(targetId);
            case LOST_ITEM -> lostItemService.lockReportableForReport(targetId);
            case LOST_ITEM_COMMENT -> lostItemCommentService.lockReportableForReport(targetId);
        }
    }

    private void incrementReportCountAndHideIfThreshold(ContentTargetType targetType, Long targetId) {
        int threshold = appSettingReader.getInt(SettingKey.REPORT_HIDE_THRESHOLD);
        switch (targetType) {
            case CHEER -> cheerService.incrementReportCountAndHideIfThreshold(targetId, threshold);
            case LOST_ITEM -> lostItemService.incrementReportCountAndHideIfThreshold(targetId, threshold);
            case LOST_ITEM_COMMENT -> lostItemCommentService.incrementReportCountAndHideIfThreshold(targetId, threshold);
        }
    }

    private List<ContentReport> findPage(Boolean reviewed, ContentTargetType targetType, Pageable pageable) {
        if (targetType == null) {
            if (reviewed == null) {
                return contentReportRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
            return reviewed
                    ? contentReportRepository.findAllByReviewedAtIsNotNullOrderByCreatedAtDesc(pageable)
                    : contentReportRepository.findAllByReviewedAtIsNullOrderByCreatedAtDesc(pageable);
        }
        if (reviewed == null) {
            return contentReportRepository.findAllByTargetTypeOrderByCreatedAtDesc(targetType, pageable);
        }
        return reviewed
                ? contentReportRepository.findAllByTargetTypeAndReviewedAtIsNotNullOrderByCreatedAtDesc(targetType, pageable)
                : contentReportRepository.findAllByTargetTypeAndReviewedAtIsNullOrderByCreatedAtDesc(targetType, pageable);
    }

    private ContentTargetStatus getTargetStatus(ContentReport report) {
        return switch (report.getTargetType()) {
            case CHEER -> cheerService.getTargetStatusForAdmin(report.getTargetId());
            case LOST_ITEM -> lostItemService.getTargetStatusForAdmin(report.getTargetId());
            case LOST_ITEM_COMMENT -> lostItemCommentService.getTargetStatusForAdmin(report.getTargetId());
        };
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userService.getUser(userId);
    }

    private ContentReport saveAndTranslate(ContentReport report) {
        try {
            return contentReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_ALREADY_EXISTS);
        }
    }
}
