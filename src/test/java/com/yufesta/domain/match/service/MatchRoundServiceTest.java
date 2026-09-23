package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchRoundServiceTest {

    private static final LocalDateTime PUBLISH_1 = LocalDateTime.of(2026, 10, 2, 16, 0);
    private static final LocalDateTime PUBLISH_2 = LocalDateTime.of(2026, 10, 2, 20, 0);

    @Mock
    private MatchRoundRepository matchRoundRepository;

    @InjectMocks
    private MatchRoundService matchRoundService;

    @Test
    void 현재_회차는_아직_발표되지_않은_첫_회차다() {
        MatchRound first = round(1, PUBLISH_1);
        MatchRound second = round(2, PUBLISH_2);
        first.open();
        when(matchRoundRepository.findAllByOrderBySeqAsc()).thenReturn(List.of(first, second));

        assertThat(matchRoundService.getCurrentRound()).isSameAs(first);
    }

    @Test
    void 일회차가_마감돼도_발표_전이면_현재_회차다() {
        MatchRound first = round(1, PUBLISH_1);
        first.open();
        first.close();
        when(matchRoundRepository.findAllByOrderBySeqAsc()).thenReturn(List.of(first, round(2, PUBLISH_2)));

        assertThat(matchRoundService.getCurrentRound().getSeq()).isEqualTo(1);
    }

    @Test
    void 일회차가_발표되면_이회차가_현재_회차다() {
        MatchRound first = round(1, PUBLISH_1);
        first.open();
        first.close();
        first.publish(PUBLISH_1);
        MatchRound second = round(2, PUBLISH_2);
        second.open();
        when(matchRoundRepository.findAllByOrderBySeqAsc()).thenReturn(List.of(first, second));

        assertThat(matchRoundService.getCurrentRound()).isSameAs(second);
    }

    @Test
    void 전부_발표되면_마지막_회차가_현재_회차다() {
        MatchRound first = round(1, PUBLISH_1);
        MatchRound second = round(2, PUBLISH_2);
        for (MatchRound round : List.of(first, second)) {
            round.open();
            round.close();
            round.publish(round.getPublishAt());
        }
        when(matchRoundRepository.findAllByOrderBySeqAsc()).thenReturn(List.of(first, second));

        assertThat(matchRoundService.getCurrentRound()).isSameAs(second);
    }

    @Test
    void 회차가_없으면_MATCH_ROUND_NOT_FOUND다() {
        when(matchRoundRepository.findAllByOrderBySeqAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> matchRoundService.getCurrentRound())
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MATCH_ROUND_NOT_FOUND);
    }

    @Test
    void 다음_회차는_seq_플러스_1이고_마지막이면_비어_있다() {
        MatchRound second = round(2, PUBLISH_2);
        when(matchRoundRepository.findBySeq(2)).thenReturn(Optional.of(second));
        when(matchRoundRepository.findBySeq(3)).thenReturn(Optional.empty());

        assertThat(matchRoundService.findNextRound(round(1, PUBLISH_1))).contains(second);
        assertThat(matchRoundService.findNextRound(second)).isEmpty();
    }

    private static MatchRound round(int seq, LocalDateTime publishAt) {
        return MatchRound.builder()
                .seq(seq)
                .openAt(publishAt.minusDays(7))
                .closeAt(publishAt.minusMinutes(10))
                .publishAt(publishAt)
                .build();
    }
}
