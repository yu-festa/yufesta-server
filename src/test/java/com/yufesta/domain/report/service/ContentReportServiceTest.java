package com.yufesta.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentReportServiceTest {

    @Mock
    private ContentReportRepository contentReportRepository;

    @Mock
    private UserService userService;

    @Mock
    private CheerService cheerService;

    @Mock
    private LostItemService lostItemService;

    @Mock
    private AppSettingReader appSettingReader;

    @InjectMocks
    private ContentReportService contentReportService;

    @Test
    void 로그인_사용자가_응원_메시지를_신고하면_잠금_후_신고를_저장하고_누적_처리한다() {
        User reporter = reporter(7L);
        when(userService.getUser(7L)).thenReturn(reporter);
        when(contentReportRepository.existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType.CHEER, 1L, 7L))
                .thenReturn(false);
        when(contentReportRepository.saveAndFlush(any(ContentReport.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        when(appSettingReader.getInt(SettingKey.REPORT_HIDE_THRESHOLD)).thenReturn(2);

        ContentReportResponse result = contentReportService.create(7L, cheerRequest());

        assertThat(result)
                .extracting(ContentReportResponse::targetType, ContentReportResponse::targetId, ContentReportResponse::reason)
                .containsExactly(ContentTargetType.CHEER, 1L, "부적절한 내용");
        ArgumentCaptor<ContentReport> captor = ArgumentCaptor.forClass(ContentReport.class);
        verify(contentReportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReporter()).isSameAs(reporter);

        InOrder inOrder = inOrder(cheerService, contentReportRepository, appSettingReader);
        inOrder.verify(cheerService).lockReportableForReport(1L);
        inOrder.verify(contentReportRepository)
                .existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType.CHEER, 1L, 7L);
        inOrder.verify(contentReportRepository).saveAndFlush(any(ContentReport.class));
        inOrder.verify(appSettingReader).getInt(SettingKey.REPORT_HIDE_THRESHOLD);
        inOrder.verify(cheerService).incrementReportCountAndHideIfThreshold(1L, 2);
    }

    @Test
    void 로그인_사용자가_분실물_게시글을_신고하면_분실물_누적_처리를_호출한다() {
        User reporter = reporter(7L);
        when(userService.getUser(7L)).thenReturn(reporter);
        when(contentReportRepository.existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType.LOST_ITEM, 2L, 7L))
                .thenReturn(false);
        when(contentReportRepository.saveAndFlush(any(ContentReport.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        when(appSettingReader.getInt(SettingKey.REPORT_HIDE_THRESHOLD)).thenReturn(2);

        contentReportService.create(7L, lostItemRequest());

        verify(lostItemService).lockReportableForReport(2L);
        verify(lostItemService).incrementReportCountAndHideIfThreshold(2L, 2);
    }

    @Test
    void 같은_사용자가_같은_콘텐츠를_다시_신고하면_중복_오류를_반환한다() {
        User reporter = reporter(7L);
        when(userService.getUser(7L)).thenReturn(reporter);
        when(contentReportRepository.existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType.CHEER, 1L, 7L))
                .thenReturn(true);

        assertThatThrownBy(() -> contentReportService.create(7L, cheerRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_REPORT_ALREADY_EXISTS);

        verify(cheerService).lockReportableForReport(1L);
        verify(contentReportRepository, never()).saveAndFlush(any());
        verify(cheerService, never()).incrementReportCountAndHideIfThreshold(any(), any(Integer.class));
    }

    @Test
    void 동시_요청이_유니크_제약에_걸리면_중복_오류로_변환한다() {
        User reporter = reporter(7L);
        when(userService.getUser(7L)).thenReturn(reporter);
        when(contentReportRepository.existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType.CHEER, 1L, 7L))
                .thenReturn(false);
        when(contentReportRepository.saveAndFlush(any(ContentReport.class)))
                .thenThrow(new DataIntegrityViolationException("uk_report_once"));

        assertThatThrownBy(() -> contentReportService.create(7L, cheerRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_REPORT_ALREADY_EXISTS);

        verify(cheerService, never()).incrementReportCountAndHideIfThreshold(any(), any(Integer.class));
    }

    @Test
    void 비로그인_사용자는_콘텐츠를_신고할_수_없다() {
        assertThatThrownBy(() -> contentReportService.create(null, cheerRequest()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private static CreateContentReportRequest cheerRequest() {
        return new CreateContentReportRequest(ContentTargetType.CHEER, 1L, " 부적절한 내용 ");
    }

    private static CreateContentReportRequest lostItemRequest() {
        return new CreateContentReportRequest(ContentTargetType.LOST_ITEM, 2L, "부적절한 내용");
    }

    private static User reporter(Long id) {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private static ContentReport saved(ContentReport report) {
        ReflectionTestUtils.setField(report, "id", 1L);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 10, 2, 14, 0));
        return report;
    }
}
