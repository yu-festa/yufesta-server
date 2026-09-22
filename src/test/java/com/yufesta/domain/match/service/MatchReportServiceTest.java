package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.match.dto.request.ReportMatchRequest;
import com.yufesta.domain.match.dto.request.ReviewMatchReportRequest;
import com.yufesta.domain.match.dto.response.AdminMatchReportResponse;
import com.yufesta.domain.match.dto.response.MatchReportResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.BlockDecision;
import com.yufesta.domain.match.enums.BlockReason;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.TargetReportCount;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.service.UserService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchReportServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 16, 30);
    private static final LocalDateTime PUBLISH_1 = LocalDateTime.of(2026, 10, 2, 16, 0);
    private static final LocalDateTime PUBLISH_2 = LocalDateTime.of(2026, 10, 2, 20, 0);
    private static final long ME = 7L;
    private static final long PARTNER = 201L;
    private static final int THRESHOLD = 2;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchRoundService matchRoundService;

    @Mock
    private UserService userService;

    @Mock
    private AppSettingReader appSettingReader;

    private MatchReportService service;
    private MatchRound round1;
    private MatchRound round2;
    private User me;
    private User partner;
    private Match match;

    @BeforeEach
    void setUp() {
        service = new MatchReportService(matchRepository, blockRepository, applicationRepository, matchRoundService,
                userService, appSettingReader, Clock.fixed(NOW.atZone(KST).toInstant(), KST));
        round1 = round(1L, 1, PUBLISH_1);
        round2 = round(2L, 2, PUBLISH_2);
        me = user(ME);
        partner = user(PARTNER);
        Application mine = application(11L, me, round1);
        Application partners = application(21L, partner, round1);
        match = match(77L, mine, partners);
    }

    // ---------- 신고 접수 ----------

    @Test
    void 발표된_회차의_내_매치를_신고하면_저장되고_임계_미달이면_제재가_없다() {
        publish(round1);
        givenReportable();
        givenValidReports(1);

        MatchReportResponse response = service.report(ME, request(" 프로필과 달라요 "));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.matchId()).isEqualTo(77L);
        assertThat(response.reason()).isEqualTo(BlockReason.FAKE);
        Block saved = captureSaved();
        assertThat(saved.getReporter()).isSameAs(me);
        assertThat(saved.getTarget()).isSameAs(partner);
        assertThat(saved.getRound()).isSameAs(round1);
        assertThat(saved.getDetail()).isEqualTo("프로필과 달라요");
        assertThat(partner.isMatchingBlocked()).isFalse();
        verify(applicationRepository, never()).delete(any());
    }

    @Test
    void 상세가_공백이면_null로_저장한다() {
        publish(round1);
        givenReportable();
        givenValidReports(1);

        service.report(ME, request("   "));

        assertThat(captureSaved().getDetail()).isNull();
    }

    @Test
    void 남의_매치를_신고하면_존재를_숨기고_MATCH_NOT_FOUND다() {
        publish(round1);
        when(matchRepository.findByIdWithParticipants(77L)).thenReturn(Optional.of(match));

        assertError(() -> service.report(999L, request(null)), ErrorCode.MATCH_NOT_FOUND);
        verify(blockRepository, never()).saveAndFlush(any());
    }

    @Test
    void 없는_매치는_MATCH_NOT_FOUND다() {
        when(matchRepository.findByIdWithParticipants(77L)).thenReturn(Optional.empty());

        assertError(() -> service.report(ME, request(null)), ErrorCode.MATCH_NOT_FOUND);
    }

    @Test
    void 발표_전_회차의_매치는_MATCH_RESULT_NOT_PUBLISHED다() {
        round1.open();
        round1.close(); // 배치는 돌았지만 발표 전이라 사용자는 카드를 본 적이 없다
        when(matchRepository.findByIdWithParticipants(77L)).thenReturn(Optional.of(match));

        assertError(() -> service.report(ME, request(null)), ErrorCode.MATCH_RESULT_NOT_PUBLISHED);
        verify(userService, never()).getUserForUpdate(any());
    }

    @Test
    void 같은_상대를_다시_신고하면_BLOCK_ALREADY_EXISTS다() {
        publish(round1);
        when(matchRepository.findByIdWithParticipants(77L)).thenReturn(Optional.of(match));
        when(userService.getUserForUpdate(PARTNER)).thenReturn(partner);
        when(blockRepository.existsByReporter_IdAndTarget_Id(ME, PARTNER)).thenReturn(true);

        assertError(() -> service.report(ME, request(null)), ErrorCode.BLOCK_ALREADY_EXISTS);
        verify(blockRepository, never()).saveAndFlush(any());
    }

    @Test
    void 선검사를_지나도_유니크_제약에_걸리면_같은_409다() {
        publish(round1);
        when(matchRepository.findByIdWithParticipants(77L)).thenReturn(Optional.of(match));
        when(userService.getUserForUpdate(PARTNER)).thenReturn(partner);
        when(blockRepository.existsByReporter_IdAndTarget_Id(ME, PARTNER)).thenReturn(false);
        when(blockRepository.saveAndFlush(any(Block.class))).thenThrow(new DataIntegrityViolationException("uk_blocks_pair"));

        assertError(() -> service.report(ME, request(null)), ErrorCode.BLOCK_ALREADY_EXISTS);
    }

    @Test
    void 비로그인이면_UNAUTHORIZED다() {
        assertError(() -> service.report(null, request(null)), ErrorCode.UNAUTHORIZED);
    }

    // ---------- 누적 제재 ----------

    @Test
    void 유효_신고가_임계에_도달하면_차단하고_접수_중인_현재_회차_신청을_삭제한다() {
        publish(round1);
        round2.open();
        Application partnersRound2 = application(31L, partner, round2);
        givenReportable();
        givenValidReports(THRESHOLD);
        when(matchRoundService.getCurrentRound()).thenReturn(round2);
        when(applicationRepository.findByUser_IdAndRound_Id(PARTNER, 2L)).thenReturn(Optional.of(partnersRound2));

        service.report(ME, request(null));

        assertThat(partner.getMatchingBlockedAt()).isEqualTo(NOW);
        verify(applicationRepository).delete(partnersRound2);
    }

    @Test
    void 임계에_도달했지만_현재_회차_신청이_없으면_차단만_한다() {
        publish(round1);
        round2.open();
        givenReportable();
        givenValidReports(THRESHOLD);
        when(matchRoundService.getCurrentRound()).thenReturn(round2);
        when(applicationRepository.findByUser_IdAndRound_Id(PARTNER, 2L)).thenReturn(Optional.empty());

        service.report(ME, request(null));

        assertThat(partner.isMatchingBlocked()).isTrue();
        verify(applicationRepository, never()).delete(any());
    }

    @Test
    void 현재_회차가_배치_후_CLOSED면_신청을_지우지_않고_차단만_한다() {
        publish(round1);
        round2.open();
        round2.close(); // matches가 그 신청을 참조하므로 삭제하지 않는다. 재실행·다음 풀 조건이 걸러낸다
        givenReportable();
        givenValidReports(THRESHOLD);
        when(matchRoundService.getCurrentRound()).thenReturn(round2);

        service.report(ME, request(null));

        assertThat(partner.isMatchingBlocked()).isTrue();
        verify(applicationRepository, never()).findByUser_IdAndRound_Id(any(), any());
        verify(applicationRepository, never()).delete(any());
    }

    @Test
    void 이미_차단된_대상은_다시_차단하거나_신청을_지우지_않는다() {
        publish(round1);
        partner.blockMatching(NOW.minusHours(1));
        givenReportable();
        givenValidReports(THRESHOLD + 1);

        service.report(ME, request(null));

        assertThat(partner.getMatchingBlockedAt()).isEqualTo(NOW.minusHours(1));
        verify(matchRoundService, never()).getCurrentRound();
    }

    // ---------- 운영자 검토 ----------

    @Test
    void CONFIRM은_임계_미달이어도_즉시_차단한다() {
        round2.open();
        Block block = block(5L, me, partner, round1);
        when(blockRepository.findById(5L)).thenReturn(Optional.of(block));
        when(userService.getUserForUpdate(PARTNER)).thenReturn(partner);
        when(blockRepository.existsByTarget_IdAndDecision(PARTNER, BlockDecision.CONFIRM)).thenReturn(true);
        when(blockRepository.countValidReports(PARTNER, BlockDecision.DISMISS)).thenReturn(1L);
        when(matchRoundService.getCurrentRound()).thenReturn(round2);
        when(applicationRepository.findByUser_IdAndRound_Id(PARTNER, 2L)).thenReturn(Optional.empty());

        AdminMatchReportResponse response = service.review(5L, new ReviewMatchReportRequest(BlockDecision.CONFIRM));

        assertThat(block.getDecision()).isEqualTo(BlockDecision.CONFIRM);
        assertThat(block.getReviewedAt()).isEqualTo(NOW);
        assertThat(partner.isMatchingBlocked()).isTrue();
        assertThat(response.targetBlocked()).isTrue();
        assertThat(response.targetReportCount()).isEqualTo(1L);
        assertThat(response.reporterUserId()).isEqualTo(ME);
        verify(blockRepository).saveAndFlush(block);
    }

    @Test
    void DISMISS로_임계_아래로_내려가면_차단을_푼다() {
        partner.blockMatching(NOW.minusHours(1));
        Block block = block(5L, me, partner, round1);
        when(blockRepository.findById(5L)).thenReturn(Optional.of(block));
        when(userService.getUserForUpdate(PARTNER)).thenReturn(partner);
        when(blockRepository.existsByTarget_IdAndDecision(PARTNER, BlockDecision.CONFIRM)).thenReturn(false);
        when(blockRepository.countValidReports(PARTNER, BlockDecision.DISMISS)).thenReturn(1L);
        when(appSettingReader.getInt(SettingKey.REPORT_BLOCK_THRESHOLD)).thenReturn(THRESHOLD);

        AdminMatchReportResponse response = service.review(5L, new ReviewMatchReportRequest(BlockDecision.DISMISS));

        assertThat(block.getDecision()).isEqualTo(BlockDecision.DISMISS);
        assertThat(partner.isMatchingBlocked()).isFalse();
        assertThat(response.targetBlocked()).isFalse();
        verify(matchRoundService, never()).getCurrentRound();
    }

    @Test
    void 없는_신고를_검토하면_BLOCK_NOT_FOUND다() {
        when(blockRepository.findById(5L)).thenReturn(Optional.empty());

        assertError(() -> service.review(5L, new ReviewMatchReportRequest(BlockDecision.CONFIRM)), ErrorCode.BLOCK_NOT_FOUND);
        verify(userService, never()).getUserForUpdate(any());
    }

    // ---------- 운영자 목록 ----------

    @Test
    void 미검토_목록에_대상별_유효_신고_수와_차단_여부가_붙는다() {
        partner.blockMatching(NOW.minusHours(1));
        Block first = block(5L, me, partner, round1);
        Block second = block(6L, user(8L), partner, round1);
        when(blockRepository.findAllByReviewedAtIsNullOrderByIdDesc(PageRequest.of(0, 20))).thenReturn(List.of(second, first));
        when(blockRepository.countValidReportsByTargetIds(Set.of(PARTNER), BlockDecision.DISMISS))
                .thenReturn(List.of(new TargetReportCount(PARTNER, 2L)));

        List<AdminMatchReportResponse> responses = service.getReports(false, 0, 20);

        assertThat(responses).extracting(AdminMatchReportResponse::id).containsExactly(6L, 5L);
        assertThat(responses.get(0).targetReportCount()).isEqualTo(2L);
        assertThat(responses.get(0).targetBlocked()).isTrue();
        assertThat(responses.get(0).roundSeq()).isEqualTo(1);
        assertThat(responses.get(0).targetUserId()).isEqualTo(PARTNER);
    }

    @Test
    void 목록이_비면_집계_쿼리를_보내지_않는다() {
        when(blockRepository.findAllByOrderByIdDesc(PageRequest.of(1, 50))).thenReturn(List.of());

        assertThat(service.getReports(null, 1, 50)).isEmpty();
        verify(blockRepository, never()).countValidReportsByTargetIds(any(), any());
    }

    @Test
    void 페이지_크기가_범위를_벗어나면_INVALID_INPUT_VALUE다() {
        assertError(() -> service.getReports(null, 0, 51), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.getReports(null, 0, 0), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.getReports(null, -1, 20), ErrorCode.INVALID_INPUT_VALUE);
    }

    // ---------- 준비 ----------

    private void givenReportable() {
        when(matchRepository.findByIdWithParticipants(77L)).thenReturn(Optional.of(match));
        when(userService.getUserForUpdate(PARTNER)).thenReturn(partner);
        when(blockRepository.existsByReporter_IdAndTarget_Id(ME, PARTNER)).thenReturn(false);
        when(blockRepository.saveAndFlush(any(Block.class))).thenAnswer(invocation -> {
            Block block = invocation.getArgument(0);
            ReflectionTestUtils.setField(block, "id", 5L);
            return block;
        });
    }

    // CONFIRM 없음 + 유효 신고 수 count. 임계는 설정값 2
    private void givenValidReports(long count) {
        when(blockRepository.existsByTarget_IdAndDecision(PARTNER, BlockDecision.CONFIRM)).thenReturn(false);
        when(blockRepository.countValidReports(PARTNER, BlockDecision.DISMISS)).thenReturn(count);
        when(appSettingReader.getInt(SettingKey.REPORT_BLOCK_THRESHOLD)).thenReturn(THRESHOLD);
    }

    private Block captureSaved() {
        ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
        verify(blockRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static ReportMatchRequest request(String detail) {
        return ReportMatchRequest.builder().matchId(77L).reason(BlockReason.FAKE).detail(detail).build();
    }

    private static void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static void publish(MatchRound round) {
        round.open();
        round.close();
        round.publish(round.getPublishAt());
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

    private static Application application(Long id, User user, MatchRound round) {
        Application application = Application.builder().user(user).round(round).instagramId("insta" + id)
                .nickname("닉" + id).gender(Gender.F).termsVersion("v1").privacyVersion("v1")
                .ageConfirmed(true).agreedAt(NOW.minusDays(1)).build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private static Match match(Long id, Application mine, Application partner) {
        Match match = Match.builder().round(mine.getRound()).application(mine).partnerApplication(partner)
                .score(new BigDecimal("2.00")).assignPass(1).build();
        ReflectionTestUtils.setField(match, "id", id);
        return match;
    }

    private static Block block(Long id, User reporter, User target, MatchRound round) {
        Block block = Block.builder().reporter(reporter).target(target).round(round).reason(BlockReason.FAKE).build();
        ReflectionTestUtils.setField(block, "id", id);
        return block;
    }
}
