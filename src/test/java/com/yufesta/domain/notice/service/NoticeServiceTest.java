package com.yufesta.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.notice.dto.request.CreateNoticeRequest;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import com.yufesta.domain.notice.entity.Notice;
import com.yufesta.domain.notice.repository.NoticeRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private NoticeService noticeService;

    @Test
    void 공지를_최신순으로_요청한_개수만큼_조회한다() {
        Notice newestNotice = notice(2L, "우천 시 공연 안내", true, LocalDateTime.of(2026, 10, 2, 15, 0));
        Notice olderNotice = notice(1L, "축제 안내", false, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(noticeRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(newestNotice, olderNotice));

        List<NoticeResponse> result = noticeService.getNotices(2);

        assertThat(result).extracting(NoticeResponse::title)
                .containsExactly("우천 시 공연 안내", "축제 안내");
        verify(noticeRepository).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void 공지_상세를_조회한다() {
        Notice notice = notice(1L, "축제 안내", false, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        NoticeResponse result = noticeService.getNotice(1L);

        assertThat(result)
                .extracting(NoticeResponse::id, NoticeResponse::title, NoticeResponse::body, NoticeResponse::banner)
                .containsExactly(1L, "축제 안내", "축제 공지 본문", false);
    }

    @Test
    void 없는_공지_상세를_조회하면_예외가_발생한다() {
        when(noticeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noticeService.getNotice(999L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTICE_NOT_FOUND));
    }

    @Test
    void 운영자가_공지_등록하면_작성자로_저장한다() {
        User admin = admin(7L);
        when(userService.getUser(7L)).thenReturn(admin);
        when(noticeRepository.save(any(Notice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoticeResponse result = noticeService.create(7L, new CreateNoticeRequest("우천 시 공연 안내", "우천 시 공연장 안내입니다.", true));

        assertThat(result)
                .extracting(NoticeResponse::title, NoticeResponse::body, NoticeResponse::banner)
                .containsExactly("우천 시 공연 안내", "우천 시 공연장 안내입니다.", true);
        org.mockito.ArgumentCaptor<Notice> captor = org.mockito.ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isSameAs(admin);
    }

    private Notice notice(Long id, String title, boolean banner, LocalDateTime createdAt) {
        Notice notice = Notice.builder()
                .title(title)
                .body("축제 공지 본문")
                .banner(banner)
                .createdBy(null)
                .build();
        ReflectionTestUtils.setField(notice, "id", id);
        ReflectionTestUtils.setField(notice, "createdAt", createdAt);
        return notice;
    }

    private User admin(Long id) {
        User user = User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId("admin-provider-id")
                .role(UserRole.STAFF)
                .loginAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
