package com.yufesta.domain.report.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.cheer.service.CheerService;
import com.yufesta.domain.lostitem.service.LostItemService;
import com.yufesta.domain.report.dto.request.CreateContentReportRequest;
import com.yufesta.domain.report.dto.response.ContentReportResponse;
import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import com.yufesta.domain.report.repository.ContentReportRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 콘텐츠 신고 접수와 누적 자동 숨김을 처리 */
@Service
@Transactional(readOnly = true)
public class ContentReportService {

    private final ContentReportRepository contentReportRepository;
    private final UserService userService;
    private final CheerService cheerService;
    private final LostItemService lostItemService;
    private final AppSettingReader appSettingReader;

    public ContentReportService(
            ContentReportRepository contentReportRepository,
            UserService userService,
            CheerService cheerService,
            LostItemService lostItemService,
            AppSettingReader appSettingReader
    ) {
        this.contentReportRepository = contentReportRepository;
        this.userService = userService;
        this.cheerService = cheerService;
        this.lostItemService = lostItemService;
        this.appSettingReader = appSettingReader;
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

    private void lockReportableTarget(ContentTargetType targetType, Long targetId) {
        switch (targetType) {
            case CHEER -> cheerService.lockReportableForReport(targetId);
            case LOST_ITEM -> lostItemService.lockReportableForReport(targetId);
        }
    }

    private void incrementReportCountAndHideIfThreshold(ContentTargetType targetType, Long targetId) {
        int threshold = appSettingReader.getInt(SettingKey.REPORT_HIDE_THRESHOLD);
        switch (targetType) {
            case CHEER -> cheerService.incrementReportCountAndHideIfThreshold(targetId, threshold);
            case LOST_ITEM -> lostItemService.incrementReportCountAndHideIfThreshold(targetId, threshold);
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
