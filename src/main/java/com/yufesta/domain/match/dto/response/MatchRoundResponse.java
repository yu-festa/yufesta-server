package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.RoundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 회차 시각과 상태. 카운트다운은 프론트가 serverNow와 이 시각들로 계산한다(FR-MT-52)
 */
@Builder
public record MatchRoundResponse(
        @Schema(description = "회차 번호", example = "1") int seq,
        @Schema(description = "상태", example = "OPEN") RoundStatus status,
        @Schema(description = "접수 시작", example = "2026-09-25T00:00:00") LocalDateTime openAt,
        @Schema(description = "접수 마감", example = "2026-10-02T15:50:00") LocalDateTime closeAt,
        @Schema(description = "발표 시각", example = "2026-10-02T16:00:00") LocalDateTime publishAt
) {

    public static MatchRoundResponse from(MatchRound round) {
        return MatchRoundResponse.builder()
                .seq(round.getSeq())
                .status(round.getStatus())
                .openAt(round.getOpenAt())
                .closeAt(round.getCloseAt())
                .publishAt(round.getPublishAt())
                .build();
    }
}
