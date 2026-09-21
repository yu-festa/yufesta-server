package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.request.ApplyMatchRequest;
import com.yufesta.domain.match.dto.request.UpdateApplicationRequest;
import com.yufesta.domain.match.dto.response.ApplicationResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 12, 0);
    private static final LocalDateTime CLOSE_AT = LocalDateTime.of(2026, 10, 2, 15, 50);
    private static final long USER_ID = 7L;
    private static final long ROUND_ID = 1L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchRoundService matchRoundService;

    @Mock
    private UserService userService;

    private ApplicationService applicationService;
    private MatchRound round;
    private User user;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(
                applicationRepository, matchRoundService, userService, Clock.fixed(NOW.atZone(KST).toInstant(), KST)
        );
        round = MatchRound.builder().seq(1).openAt(NOW.minusDays(7)).closeAt(CLOSE_AT).publishAt(CLOSE_AT.plusMinutes(10)).build();
        round.open();
        ReflectionTestUtils.setField(round, "id", ROUND_ID);
        user = User.builder().provider(OAuthProvider.KAKAO).providerUserId("k").role(UserRole.USER).loginAt(NOW).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void 신청하면_인스타_ID를_정규화하고_동의_시각을_Clock으로_기록한다() {
        givenOpenRoundAndUser();
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.empty());
        when(applicationRepository.existsByRound_IdAndInstagramId(ROUND_ID, "yu.festa")).thenReturn(false);
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, request(" @Yu.Festa "));

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).saveAndFlush(captor.capture());
        Application saved = captor.getValue();
        assertThat(saved.getInstagramId()).isEqualTo("yu.festa");
        assertThat(saved.getAgreedAt()).isEqualTo(NOW);
        assertThat(saved.isAgeConfirmed()).isTrue();
        assertThat(saved.getEntryType()).isEqualTo(EntryType.NEW);
        assertThat(response.instagramId()).isEqualTo("yu.festa");
        assertThat(response.tags()).containsExactly(MatchTag.CAFE, MatchTag.MUSIC);
    }

    @Test
    void 매칭_차단_회원은_USER_MATCHING_BLOCKED다() {
        ReflectionTestUtils.setField(user, "matchingBlockedAt", NOW.minusDays(1));
        when(userService.getUser(USER_ID)).thenReturn(user);

        assertError(() -> applicationService.apply(USER_ID, request("yu.festa")), ErrorCode.USER_MATCHING_BLOCKED);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void 마감_후_신청하면_MATCH_ROUND_NOT_OPEN이다() {
        ReflectionTestUtils.setField(round, "closeAt", NOW.minusMinutes(1));
        givenOpenRoundAndUser();

        assertError(() -> applicationService.apply(USER_ID, request("yu.festa")), ErrorCode.MATCH_ROUND_NOT_OPEN);
    }

    @Test
    void 비로그인이면_UNAUTHORIZED다() {
        assertError(() -> applicationService.apply(null, request("yu.festa")), ErrorCode.UNAUTHORIZED);
        assertError(() -> applicationService.getMine(null), ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 허용되지_않는_인스타_ID_형식은_INVALID_INPUT_VALUE다() {
        givenOpenRoundAndUser();

        assertError(() -> applicationService.apply(USER_ID, request("yu festa")), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> applicationService.apply(USER_ID, request("a".repeat(31))), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 같은_회차에_유효한_신청이_있으면_APPLICATION_ALREADY_EXISTS다() {
        givenOpenRoundAndUser();
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.of(application("mine")));

        assertError(() -> applicationService.apply(USER_ID, request("other")), ErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    @Test
    void 다른_사람이_쓰는_인스타_ID면_선검사에서_APPLICATION_INSTAGRAM_DUPLICATE다() {
        givenOpenRoundAndUser();
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.empty());
        when(applicationRepository.existsByRound_IdAndInstagramId(ROUND_ID, "taken")).thenReturn(true);

        assertError(() -> applicationService.apply(USER_ID, request("taken")), ErrorCode.APPLICATION_INSTAGRAM_DUPLICATE);
    }

    @Test
    void 선검사를_통과해도_DB_제약_위반이면_제약_이름으로_409를_나눈다() {
        givenOpenRoundAndUser();
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.empty());
        when(applicationRepository.existsByRound_IdAndInstagramId(ROUND_ID, "taken")).thenReturn(false);
        when(applicationRepository.saveAndFlush(any(Application.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '1-taken' for key 'applications.uk_app_round_insta'"))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'applications.UK_APP_USER_ROUND'"));

        assertError(() -> applicationService.apply(USER_ID, request("taken")), ErrorCode.APPLICATION_INSTAGRAM_DUPLICATE);
        assertError(() -> applicationService.apply(USER_ID, request("taken")), ErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    @Test
    void 취소한_신청이_있으면_새_행_대신_그_행을_되살린다() {
        givenOpenRoundAndUser();
        Application canceled = application("old.id");
        ReflectionTestUtils.setField(canceled, "id", 42L);
        canceled.cancel(NOW.minusHours(1));
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.of(canceled));
        when(applicationRepository.existsByRound_IdAndInstagramId(ROUND_ID, "new.id")).thenReturn(false);
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = applicationService.apply(USER_ID, request("new.id"));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(canceled.isCanceled()).isFalse();
        assertThat(canceled.getInstagramId()).isEqualTo("new.id");
        assertThat(canceled.getAgreedAt()).isEqualTo(NOW);
    }

    @Test
    void 취소한_내_신청의_인스타_ID를_그대로_다시_쓰면_중복이_아니다() {
        givenOpenRoundAndUser();
        Application canceled = application("same.id");
        canceled.cancel(NOW.minusHours(1));
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.of(canceled));
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        applicationService.apply(USER_ID, request("same.id"));

        verify(applicationRepository, never()).existsByRound_IdAndInstagramId(any(), any());
    }

    @Test
    void 내_신청_조회는_취소됐거나_없으면_APPLICATION_NOT_FOUND다() {
        when(matchRoundService.getCurrentRound()).thenReturn(round);
        Application canceled = application("mine");
        canceled.cancel(NOW);
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID))
                .thenReturn(Optional.of(canceled))
                .thenReturn(Optional.empty());

        assertError(() -> applicationService.getMine(USER_ID), ErrorCode.APPLICATION_NOT_FOUND);
        assertError(() -> applicationService.getMine(USER_ID), ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void 수정은_태그를_교체하고_인스타_ID_중복을_다시_검사한다() {
        when(matchRoundService.getCurrentRound()).thenReturn(round);
        Application mine = application("mine");
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.of(mine));
        when(applicationRepository.existsByRound_IdAndInstagramId(ROUND_ID, "taken")).thenReturn(true);
        when(applicationRepository.saveAndFlush(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertError(() -> applicationService.update(USER_ID, updateRequest("taken", Set.of(MatchTag.PET))),
                ErrorCode.APPLICATION_INSTAGRAM_DUPLICATE);

        ApplicationResponse response = applicationService.update(USER_ID, updateRequest("Mine", Set.of(MatchTag.PET)));
        assertThat(response.instagramId()).isEqualTo("mine");
        assertThat(mine.getTags()).containsExactly(MatchTag.PET);
    }

    @Test
    void 취소는_canceled_at을_Clock_시각으로_기록하고_마감_후에는_불가하다() {
        when(matchRoundService.getCurrentRound()).thenReturn(round);
        Application mine = application("mine");
        when(applicationRepository.findByUser_IdAndRound_Id(USER_ID, ROUND_ID)).thenReturn(Optional.of(mine));

        applicationService.cancel(USER_ID);
        assertThat(mine.getCanceledAt()).isEqualTo(NOW);

        round.close();
        assertError(() -> applicationService.cancel(USER_ID), ErrorCode.MATCH_ROUND_NOT_OPEN);
    }

    private void givenOpenRoundAndUser() {
        when(userService.getUser(USER_ID)).thenReturn(user);
        when(matchRoundService.getCurrentRound()).thenReturn(round);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private static ApplyMatchRequest request(String instagramId) {
        return ApplyMatchRequest.builder()
                .instagramId(instagramId)
                .nickname("펭귄")
                .gender(Gender.F)
                .ageBand(AgeBand.A22_24)
                .tags(Set.of(MatchTag.MUSIC, MatchTag.CAFE))
                .intro("같이 공연 봐요")
                .termsVersion("v1")
                .privacyVersion("v1")
                .ageConfirmed(true)
                .build();
    }

    private static UpdateApplicationRequest updateRequest(String instagramId, Set<MatchTag> tags) {
        return UpdateApplicationRequest.builder()
                .instagramId(instagramId)
                .nickname("수달")
                .gender(Gender.F)
                .tags(tags)
                .build();
    }

    private Application application(String instagramId) {
        return Application.builder()
                .user(user)
                .round(round)
                .instagramId(instagramId)
                .nickname("펭귄")
                .gender(Gender.F)
                .tags(Set.of(MatchTag.MUSIC))
                .termsVersion("v1")
                .privacyVersion("v1")
                .ageConfirmed(true)
                .agreedAt(NOW.minusDays(1))
                .build();
    }
}
