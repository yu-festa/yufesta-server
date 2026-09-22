package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import com.yufesta.domain.match.repository.UserIdPair;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchRoundBatchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 15, 50);
    private static final LocalDateTime PUBLISH_1 = LocalDateTime.of(2026, 10, 2, 16, 0);
    private static final LocalDateTime PUBLISH_2 = LocalDateTime.of(2026, 10, 2, 20, 0);

    @Mock
    private MatchRoundRepository matchRoundRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private AppSettingReader appSettingReader;

    private MatchRoundBatchService service;
    private MatchRound round1;
    private MatchRound round2;
    private Application w1;
    private Application w2;
    private Application m1;
    private Application m2;
    private Application m3;

    @BeforeEach
    void setUp() {
        service = new MatchRoundBatchService(
                matchRoundRepository, applicationRepository, matchRepository, blockRepository, appSettingReader,
                Clock.fixed(NOW.atZone(KST).toInstant(), KST)
        );
        round1 = round(1L, 1, PUBLISH_1);
        round2 = round(2L, 2, PUBLISH_2);
        w1 = application(11L, 101L, round1, Gender.F, Set.of(MatchTag.MUSIC, MatchTag.CAFE));
        w2 = application(12L, 102L, round1, Gender.F, Set.of(MatchTag.GAME));
        m1 = application(21L, 201L, round1, Gender.M, Set.of(MatchTag.MUSIC, MatchTag.CAFE));
        m2 = application(22L, 202L, round1, Gender.M, Set.of(MatchTag.GAME));
        m3 = application(23L, 203L, round1, Gender.M, Set.of());
    }

    @Test
    void 마감하면_CLOSED로_바꾸고_배치_결과를_쌍당_2행_저장하고_executed_at을_기록한다() {
        round1.open();
        givenLockedRound(round1);
        givenPool(round1, w1, w2, m1, m2, m3);
        givenNoExclusions(round1);
        givenSettings();
        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        RoundBatchResultResponse result = service.close(1L);

        assertThat(round1.getStatus()).isEqualTo(RoundStatus.CLOSED);
        assertThat(round1.getExecutedAt()).isEqualTo(NOW);

        ArgumentCaptor<List<Match>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchRepository).saveAll(captor.capture());
        List<Match> rows = captor.getValue();
        assertThat(rows).hasSize(6); // 여 2명 × N 안에서 남 3명 전원 → 3쌍 × 2행
        assertThat(rows).allMatch(row -> row.getRound() == round1);
        assertThat(rows).filteredOn(row -> row.getApplication() == w1 && row.getPartnerApplication() == m1)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getScore()).isEqualByComparingTo("2.00");
                    assertThat(row.getAssignPass()).isEqualTo(1);
                });
        assertThat(rows).filteredOn(row -> row.getApplication() == m1 && row.getPartnerApplication() == w1).hasSize(1);

        assertThat(result.poolSize()).isEqualTo(5);
        assertThat(result.poolMen()).isEqualTo(3);
        assertThat(result.pairCount()).isEqualTo(3);
        assertThat(result.matchedApplicants()).isEqualTo(5);
        assertThat(result.unmatchedApplicants()).isZero();
    }

    @Test
    void OPEN이_아닌_회차를_마감하면_MATCH_ROUND_INVALID_STATUS이고_배치가_돌지_않는다() {
        givenLockedRound(round1); // SCHEDULED

        assertError(() -> service.close(1L), ErrorCode.MATCH_ROUND_INVALID_STATUS);
        verify(matchRepository, never()).saveAll(anyList());
    }

    @Test
    void 차단_쌍과_이전_회차_매칭_쌍은_제외된다() {
        round1.open();
        givenLockedRound(round1);
        givenPool(round1, w1, m1, m2);
        givenSettings();
        // w1-m1은 차단(방향 반대로 저장), w1-m2는 이전 회차 매칭 → 둘 다 붙을 수 없어 결과 0쌍
        when(blockRepository.findAllUserIdPairs()).thenReturn(List.of(new UserIdPair(201L, 101L)));
        when(matchRepository.findMatchedUserIdPairsBeforeSeq(1)).thenReturn(List.of(new UserIdPair(101L, 202L)));
        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        RoundBatchResultResponse result = service.close(1L);

        assertThat(result.pairCount()).isZero();
        assertThat(result.unmatchedApplicants()).isEqualTo(3);
    }

    @Test
    void 가중치와_N은_설정에서_읽는다() {
        round1.open();
        givenLockedRound(round1);
        givenPool(round1, w1, m1);
        givenNoExclusions(round1);
        givenSettings();
        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.close(1L);

        verify(appSettingReader).getDecimal(SettingKey.MATCH_WEIGHT_TAG);
        verify(appSettingReader).getInt(SettingKey.MATCH_MAX_PARTNERS);
    }

    @Test
    void 발표하면_PUBLISHED가_되고_미매칭_신청이_다음_회차에_CARRIED로_복사되며_다음_회차가_열린다() {
        round1.open();
        round1.close();
        givenLockedRound(round1);
        when(matchRoundRepository.findBySeq(2)).thenReturn(Optional.of(round2));
        when(matchRepository.findMatchedApplicationIdsByRoundId(1L)).thenReturn(Set.of(11L, 12L, 21L, 22L));
        givenPool(round1, w1, w2, m1, m2, m3);
        when(applicationRepository.existsByUser_IdAndRound_Id(203L, 2L)).thenReturn(false);
        when(applicationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(1L);

        assertThat(round1.getStatus()).isEqualTo(RoundStatus.PUBLISHED);
        assertThat(round1.getPublishedAt()).isEqualTo(NOW);
        assertThat(round2.getStatus()).isEqualTo(RoundStatus.OPEN);

        ArgumentCaptor<List<Application>> captor = ArgumentCaptor.forClass(List.class);
        verify(applicationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(copy -> {
            assertThat(copy.getRound()).isSameAs(round2);
            assertThat(copy.getUser()).isSameAs(m3.getUser());
            assertThat(copy.getEntryType()).isEqualTo(EntryType.CARRIED);
            assertThat(copy.getSourceApplication()).isSameAs(m3);
            assertThat(copy.getInstagramId()).isEqualTo(m3.getInstagramId());
            assertThat(copy.getTags()).isEqualTo(m3.getTags());
            assertThat(copy.getAgreedAt()).isEqualTo(m3.getAgreedAt());
        });
    }

    @Test
    void 다음_회차에_이미_신청한_회원은_이월하지_않는다() {
        round1.open();
        round1.close();
        round2.open();
        givenLockedRound(round1);
        when(matchRoundRepository.findBySeq(2)).thenReturn(Optional.of(round2));
        when(matchRepository.findMatchedApplicationIdsByRoundId(1L)).thenReturn(Set.of());
        givenPool(round1, m3);
        when(applicationRepository.existsByUser_IdAndRound_Id(203L, 2L)).thenReturn(true);
        when(applicationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(1L);

        ArgumentCaptor<List<Application>> captor = ArgumentCaptor.forClass(List.class);
        verify(applicationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
        assertThat(round2.getStatus()).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    void 마지막_회차_발표는_이월이_없다() {
        round2.open();
        round2.close();
        givenLockedRound(round2);
        when(matchRoundRepository.findBySeq(3)).thenReturn(Optional.empty());

        service.publish(2L);

        assertThat(round2.getStatus()).isEqualTo(RoundStatus.PUBLISHED);
        verify(applicationRepository, never()).saveAll(anyList());
    }

    @Test
    void CLOSED가_아닌_회차를_발표하면_MATCH_ROUND_INVALID_STATUS다() {
        round1.open();
        givenLockedRound(round1);

        assertError(() -> service.publish(1L), ErrorCode.MATCH_ROUND_INVALID_STATUS);
        assertThat(round1.getStatus()).isEqualTo(RoundStatus.OPEN);
    }

    @Test
    void 재실행은_CLOSED에서만_되고_기존_결과를_지운_뒤_다시_저장한다() {
        round1.open();
        round1.close();
        givenLockedRound(round1);
        givenPool(round1, w1, m1);
        givenNoExclusions(round1);
        givenSettings();
        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        RoundBatchResultResponse result = service.rerun(1L);

        verify(matchRepository).deleteAllByRoundId(1L);
        verify(matchRepository).saveAll(anyList());
        assertThat(result.pairCount()).isEqualTo(1);

        round1.publish(NOW);
        assertError(() -> service.rerun(1L), ErrorCode.MATCH_ROUND_INVALID_STATUS);
    }

    @Test
    void 없는_회차면_MATCH_ROUND_NOT_FOUND다() {
        when(matchRoundRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertError(() -> service.close(99L), ErrorCode.MATCH_ROUND_NOT_FOUND);
    }

    private void givenLockedRound(MatchRound round) {
        when(matchRoundRepository.findByIdForUpdate(round.getId())).thenReturn(Optional.of(round));
    }

    private void givenPool(MatchRound round, Application... applications) {
        when(applicationRepository.findPoolByRoundId(round.getId())).thenReturn(List.of(applications));
    }

    private void givenNoExclusions(MatchRound round) {
        when(blockRepository.findAllUserIdPairs()).thenReturn(List.of());
        when(matchRepository.findMatchedUserIdPairsBeforeSeq(round.getSeq())).thenReturn(List.of());
    }

    private void givenSettings() {
        when(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_TAG)).thenReturn(new BigDecimal("1"));
        when(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_SLOT)).thenReturn(new BigDecimal("2"));
        when(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_SAME)).thenReturn(new BigDecimal("1"));
        when(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_ADJACENT)).thenReturn(new BigDecimal("0.5"));
        when(appSettingReader.getInt(SettingKey.MATCH_MAX_PARTNERS)).thenReturn(3);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private static MatchRound round(Long id, int seq, LocalDateTime publishAt) {
        MatchRound round = MatchRound.builder()
                .seq(seq)
                .openAt(publishAt.minusDays(7))
                .closeAt(publishAt.minusMinutes(10))
                .publishAt(publishAt)
                .build();
        ReflectionTestUtils.setField(round, "id", id);
        return round;
    }

    private static Application application(Long id, Long userId, MatchRound round, Gender gender, Set<MatchTag> tags) {
        User user = User.builder().provider(OAuthProvider.KAKAO).providerUserId("k" + userId).role(UserRole.USER).loginAt(NOW).build();
        ReflectionTestUtils.setField(user, "id", userId);
        Application application = Application.builder()
                .user(user)
                .round(round)
                .instagramId("insta" + id)
                .nickname("닉" + id)
                .gender(gender)
                .tags(tags)
                .termsVersion("v1")
                .privacyVersion("v1")
                .ageConfirmed(true)
                .agreedAt(NOW.minusDays(1))
                .build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }
}
