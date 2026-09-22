package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.request.UpdateRoundTimesRequest;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchRoundAdminServiceTest {

    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 9, 25, 0, 0);
    private static final LocalDateTime PUBLISH_AT = LocalDateTime.of(2026, 10, 2, 16, 0);

    @Mock
    private MatchRoundRepository matchRoundRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private MatchRoundAdminService service;

    private MatchRound round;

    @BeforeEach
    void setUp() {
        round = MatchRound.builder().seq(1).openAt(OPEN_AT).closeAt(PUBLISH_AT.minusMinutes(10)).publishAt(PUBLISH_AT).build();
        ReflectionTestUtils.setField(round, "id", 1L);
    }

    @Test
    void 시각_수정은_마감이_발표_10분_전이고_시작이_마감_전일_때만_된다() {
        when(matchRoundRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(round));
        LocalDateTime newPublish = LocalDateTime.of(2026, 10, 2, 17, 0);

        service.updateTimes(1L, times(OPEN_AT, newPublish.minusMinutes(10), newPublish));
        assertThat(round.getPublishAt()).isEqualTo(newPublish);
        assertThat(round.getCloseAt()).isEqualTo(newPublish.minusMinutes(10));

        assertError(() -> service.updateTimes(1L, times(OPEN_AT, newPublish.minusMinutes(5), newPublish)), ErrorCode.INVALID_INPUT_VALUE);
        assertError(() -> service.updateTimes(1L, times(newPublish, newPublish.minusMinutes(10), newPublish)), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 발표된_회차의_시각은_수정할_수_없다() {
        round.open();
        round.close();
        round.publish(PUBLISH_AT);
        when(matchRoundRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(round));

        assertError(() -> service.updateTimes(1L, times(OPEN_AT, PUBLISH_AT.minusMinutes(10), PUBLISH_AT)), ErrorCode.MATCH_ROUND_INVALID_STATUS);
    }

    @Test
    void 수동_오픈은_SCHEDULED에서만_된다() {
        when(matchRoundRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(round));

        assertThat(service.open(1L).status()).isEqualTo(RoundStatus.OPEN);
        assertError(() -> service.open(1L), ErrorCode.MATCH_ROUND_INVALID_STATUS);
    }

    @Test
    void 결과_요약은_풀_인원과_쌍_수와_평균_점수를_계산한다() {
        when(matchRoundRepository.findById(1L)).thenReturn(Optional.of(round));
        Application w = application(11L, Gender.F);
        Application m = application(21L, Gender.M);
        Application unmatched = application(22L, Gender.M);
        when(applicationRepository.findPoolByRoundId(1L)).thenReturn(List.of(w, m, unmatched));
        when(matchRepository.findAllByRound_Id(1L)).thenReturn(List.of(match(w, m, "3.50", 1), match(m, w, "3.50", 1)));

        RoundBatchResultResponse result = service.getResult(1L);

        assertThat(result.poolSize()).isEqualTo(3);
        assertThat(result.poolMen()).isEqualTo(2);
        assertThat(result.poolWomen()).isEqualTo(1);
        assertThat(result.pairCount()).isEqualTo(1);
        assertThat(result.firstPassPairs()).isEqualTo(1);
        assertThat(result.secondPassPairs()).isZero();
        assertThat(result.matchedApplicants()).isEqualTo(2);
        assertThat(result.unmatchedApplicants()).isEqualTo(1);
        assertThat(result.averageScore()).isEqualByComparingTo("3.50");
    }

    @Test
    void 배치_전_결과_요약은_쌍이_없고_평균이_null이다() {
        when(matchRoundRepository.findById(1L)).thenReturn(Optional.of(round));
        when(applicationRepository.findPoolByRoundId(1L)).thenReturn(List.of(application(11L, Gender.F)));
        when(matchRepository.findAllByRound_Id(1L)).thenReturn(List.of());

        RoundBatchResultResponse result = service.getResult(1L);

        assertThat(result.pairCount()).isZero();
        assertThat(result.unmatchedApplicants()).isEqualTo(1);
        assertThat(result.averageScore()).isNull();
    }

    private static UpdateRoundTimesRequest times(LocalDateTime open, LocalDateTime close, LocalDateTime publish) {
        return UpdateRoundTimesRequest.builder().openAt(open).closeAt(close).publishAt(publish).build();
    }

    private Application application(Long id, Gender gender) {
        Application application = Application.builder()
                .round(round).instagramId("i" + id).nickname("닉").gender(gender)
                .termsVersion("v1").privacyVersion("v1").ageConfirmed(true).agreedAt(OPEN_AT)
                .build();
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private Match match(Application a, Application b, String score, int pass) {
        return Match.builder().round(round).application(a).partnerApplication(b).score(new BigDecimal(score)).assignPass(pass).build();
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
