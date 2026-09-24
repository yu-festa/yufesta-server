package com.yufesta.domain.match.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.enums.RoundStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MatchRoundTest {

    private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime CLOSE_AT = LocalDateTime.of(2026, 10, 8, 15, 50);
    private static final LocalDateTime PUBLISH_AT = LocalDateTime.of(2026, 10, 8, 16, 0);

    @Test
    void 상태는_SCHEDULED_OPEN_CLOSED_PUBLISHED_순서로만_바뀐다() {
        MatchRound round = round();
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SCHEDULED);

        round.open();
        round.close();
        round.publish(PUBLISH_AT);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.PUBLISHED);
        assertThat(round.getPublishedAt()).isEqualTo(PUBLISH_AT);
        assertThat(round.isPublished()).isTrue();
    }

    @Test
    void 순서를_건너뛰면_MATCH_ROUND_INVALID_STATUS다() {
        MatchRound round = round();

        assertThatThrownBy(round::close)
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MATCH_ROUND_INVALID_STATUS);
        assertThatThrownBy(() -> round.publish(PUBLISH_AT))
                .isInstanceOf(CustomException.class);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.SCHEDULED);
    }

    @Test
    void 접수는_OPEN이고_마감_전일_때만_가능하다() {
        MatchRound round = round();
        assertThat(round.isAcceptingAt(CLOSE_AT.minusHours(1))).isFalse();

        round.open();
        assertThat(round.isAcceptingAt(CLOSE_AT.minusSeconds(1))).isTrue();
        assertThat(round.isAcceptingAt(CLOSE_AT)).isFalse();

        round.close();
        assertThat(round.isAcceptingAt(CLOSE_AT.minusHours(1))).isFalse();
    }

    @Test
    void 보고_싶은_공연은_발표_시각_이후_시작만_허용한다() {
        MatchRound round = round();

        assertThat(round.allowsWantedSlot(PUBLISH_AT.minusMinutes(1))).isFalse();
        assertThat(round.allowsWantedSlot(PUBLISH_AT)).isTrue();
        assertThat(round.allowsWantedSlot(PUBLISH_AT.plusHours(2))).isTrue();
    }

    private static MatchRound round() {
        return MatchRound.builder().seq(1).openAt(OPEN_AT).closeAt(CLOSE_AT).publishAt(PUBLISH_AT).build();
    }
}
