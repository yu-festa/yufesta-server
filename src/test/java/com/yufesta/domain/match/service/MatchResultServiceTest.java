package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.response.MatchResultResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.BlockReason;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 16, 30);
    private static final LocalDateTime PUBLISH_1 = LocalDateTime.of(2026, 10, 2, 16, 0);
    private static final LocalDateTime PUBLISH_2 = LocalDateTime.of(2026, 10, 2, 20, 0);
    private static final long ME = 7L;

    @Mock
    private MatchRoundService matchRoundService;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private BlockRepository blockRepository;

    private MatchResultService service;
    private MatchRound round1;
    private MatchRound round2;
    private User me;
    private Application mine;

    @BeforeEach
    void setUp() {
        service = new MatchResultService(matchRoundService, applicationRepository, matchRepository, blockRepository,
                Clock.fixed(NOW.atZone(KST).toInstant(), KST));
        round1 = round(1L, 1, PUBLISH_1);
        round2 = round(2L, 2, PUBLISH_2);
        me = user(ME);
        mine = application(11L, me, round1, "펭귄", Set.of(MatchTag.MUSIC, MatchTag.CAFE));
    }

    @Test
    void 발표_전_회차는_matches가_있어도_MATCH_RESULT_NOT_PUBLISHED다() {
        round1.open();
        round1.close(); // 배치는 돌았지만 발표 전
        when(matchRoundService.getRoundBySeq(1)).thenReturn(round1);

        assertError(() -> service.getMyResult(ME, 1), ErrorCode.MATCH_RESULT_NOT_PUBLISHED);
        verify(matchRepository, never()).findAllByApplicationId(11L);
    }

    @Test
    void 발표된_회차가_하나도_없으면_MATCH_RESULT_NOT_PUBLISHED다() {
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.empty());

        assertError(() -> service.getMyResult(ME, null), ErrorCode.MATCH_RESULT_NOT_PUBLISHED);
    }

    @Test
    void 발표_회차에_내_유효_신청이_없으면_APPLICATION_NOT_FOUND다() {
        publish(round1);
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(round1));
        when(applicationRepository.findByUser_IdAndRound_Id(ME, 1L)).thenReturn(Optional.empty());

        assertError(() -> service.getMyResult(ME, null), ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void 카드는_점수순이고_공통_태그를_표시하며_식별_정보가_없다() {
        publish(round1);
        givenMyApplicationIn(round1);
        Application partnerA = application(21L, user(201L), round1, "수달", Set.of(MatchTag.MUSIC, MatchTag.GAME));
        Application partnerB = application(22L, user(202L), round1, "판다", Set.of(MatchTag.PET));
        when(matchRepository.findAllByApplicationId(11L)).thenReturn(List.of(
                match(77L, mine, partnerA, "3.50"), match(78L, mine, partnerB, "1.00")));
        when(blockRepository.findAllByReporter_Id(ME)).thenReturn(List.of());
        when(matchRoundService.findNextRound(round1)).thenReturn(Optional.empty());

        MatchResultResponse result = service.getMyResult(ME, null);

        assertThat(result.status()).isEqualTo(MatchResultStatus.MATCHED);
        assertThat(result.partners()).extracting(p -> p.matchId()).containsExactly(77L, 78L);
        assertThat(result.partners().get(0).nickname()).isEqualTo("수달");
        assertThat(result.partners().get(0).commonTags()).containsExactly(MatchTag.MUSIC);
        assertThat(result.partners().get(0).instagramId()).isEqualTo("insta21");
        assertThat(result.partners().get(1).commonTags()).isEmpty();
        assertThat(result.nextRoundSeq()).isNull();
        assertThat(result.canRejoin()).isFalse();
    }

    @Test
    void 카드에_상대가_고른_공연과_나와_같은_공연인지_표시한다() {
        publish(round1);
        givenMyApplicationIn(round1);
        mine.changeWantedSlot(slot(4L, PUBLISH_1.plusMinutes(15)));
        Application same = application(21L, user(201L), round1, "수달", Set.of());
        same.changeWantedSlot(slot(4L, PUBLISH_1.plusMinutes(15)));
        Application none = application(22L, user(202L), round1, "판다", Set.of());
        when(matchRepository.findAllByApplicationId(11L)).thenReturn(List.of(
                match(77L, mine, same, "3.50"), match(78L, mine, none, "1.00")));
        when(blockRepository.findAllByReporter_Id(ME)).thenReturn(List.of());
        when(matchRoundService.findNextRound(round1)).thenReturn(Optional.empty());

        MatchResultResponse result = service.getMyResult(ME, null);

        assertThat(result.partners().get(0).wantedSlot().title()).isEqualTo("공연 4");
        assertThat(result.partners().get(0).sameSlot()).isTrue();
        assertThat(result.partners().get(1).wantedSlot()).isNull();
        assertThat(result.partners().get(1).sameSlot()).isFalse();
    }

    @Test
    void 내가_신고한_상대의_카드는_제외되지만_매칭_상태는_유지된다() {
        publish(round1);
        givenMyApplicationIn(round1);
        Application reported = application(21L, user(201L), round1, "수달", Set.of());
        when(matchRepository.findAllByApplicationId(11L)).thenReturn(List.of(match(77L, mine, reported, "2.00")));
        when(blockRepository.findAllByReporter_Id(ME)).thenReturn(List.of(
                Block.builder().reporter(me).target(reported.getUser()).round(round1).reason(BlockReason.FAKE).build()));
        when(matchRoundService.findNextRound(round1)).thenReturn(Optional.empty());

        MatchResultResponse result = service.getMyResult(ME, null);

        assertThat(result.partners()).isEmpty();
        assertThat(result.status()).isEqualTo(MatchResultStatus.MATCHED);
    }

    @Test
    void 미매칭이면_UNMATCHED이고_이월된_신청이_있으면_hasNextRoundApplication이_true다() {
        publish(round1);
        round2.open();
        givenMyApplicationIn(round1);
        when(matchRepository.findAllByApplicationId(11L)).thenReturn(List.of());
        when(blockRepository.findAllByReporter_Id(ME)).thenReturn(List.of());
        when(matchRoundService.findNextRound(round1)).thenReturn(Optional.of(round2));
        when(applicationRepository.findByUser_IdAndRound_Id(ME, 2L))
                .thenReturn(Optional.of(application(31L, me, round2, "펭귄", Set.of())));

        MatchResultResponse result = service.getMyResult(ME, null);

        assertThat(result.status()).isEqualTo(MatchResultStatus.UNMATCHED);
        assertThat(result.nextRoundSeq()).isEqualTo(2);
        assertThat(result.hasNextRoundApplication()).isTrue();
        assertThat(result.canRejoin()).isFalse();
    }

    @Test
    void canRejoin은_매칭되고_다음_회차가_접수_중이며_다음_회차_신청이_없을_때만_true다() {
        publish(round1);
        round2.open();
        givenMyApplicationIn(round1);
        when(matchRepository.findAllByApplicationId(11L))
                .thenReturn(List.of(match(77L, mine, application(21L, user(201L), round1, "수달", Set.of()), "1.00")));
        when(blockRepository.findAllByReporter_Id(ME)).thenReturn(List.of());
        when(matchRoundService.findNextRound(round1)).thenReturn(Optional.of(round2));
        when(applicationRepository.findByUser_IdAndRound_Id(ME, 2L)).thenReturn(Optional.empty());

        assertThat(service.getMyResult(ME, null).canRejoin()).isTrue();

        round2.close(); // 접수 종료 → 재참여 불가
        assertThat(service.getMyResult(ME, null).canRejoin()).isFalse();
    }

    @Test
    void 비로그인이면_UNAUTHORIZED다() {
        assertError(() -> service.getMyResult(null, null), ErrorCode.UNAUTHORIZED);
    }

    private void publish(MatchRound round) {
        round.open();
        round.close();
        round.publish(round.getPublishAt());
    }

    private void givenMyApplicationIn(MatchRound round) {
        when(matchRoundService.findLatestPublishedRound()).thenReturn(Optional.of(round));
        when(applicationRepository.findByUser_IdAndRound_Id(ME, round.getId())).thenReturn(Optional.of(mine));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(expected);
    }


    private static com.yufesta.domain.timetable.entity.TimetableSlot slot(Long id, LocalDateTime startAt) {
        com.yufesta.domain.place.entity.Place stage = com.yufesta.domain.place.entity.Place.builder()
                .name("중앙 무대").category(com.yufesta.domain.place.enums.PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build();
        ReflectionTestUtils.setField(stage, "id", 1L);
        com.yufesta.domain.timetable.entity.TimetableSlot slot = com.yufesta.domain.timetable.entity.TimetableSlot.builder()
                .sortOrder(id.intValue()).title("공연 " + id).slotType(com.yufesta.domain.timetable.enums.SlotType.CLUB)
                .startAt(startAt).endAt(startAt.plusMinutes(30)).stage(stage)
                .build();
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private static MatchRound round(Long id, int seq, LocalDateTime publishAt) {
        MatchRound round = MatchRound.builder().seq(seq).openAt(publishAt.minusDays(7))
                .closeAt(publishAt.minusMinutes(10)).publishAt(publishAt).build();
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    private static User user(Long id) {
        User user = User.builder().provider(OAuthProvider.KAKAO).providerUserId("k" + id).role(UserRole.USER).loginAt(NOW).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Application application(Long id, User user, MatchRound round, String nickname, Set<MatchTag> tags) {
        Application application = Application.builder().user(user).round(round).instagramId("insta" + id)
                .nickname(nickname).gender(Gender.F).tags(tags).termsVersion("v1").privacyVersion("v1")
                .ageConfirmed(true).agreedAt(NOW.minusDays(1)).build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private static Match match(Long id, Application mine, Application partner, String score) {
        Match match = Match.builder().round(mine.getRound()).application(mine).partnerApplication(partner)
                .score(new BigDecimal(score)).assignPass(1).build();
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }
}
