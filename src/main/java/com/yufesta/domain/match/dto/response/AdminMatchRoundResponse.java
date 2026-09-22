package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.RoundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 운영자용 회차 정보. 공개용과 달리 id와 실행·발표 시각을 포함한다
 */
@Builder
public record AdminMatchRoundResponse(
        @Schema(description = "회차 ID(운영 API 경로에 사용)", example = "1") Long id,
        @Schema(description = "회차 번호", example = "1") int seq,
        @Schema(description = "상태", example = "CLOSED") RoundStatus status,
        LocalDateTime openAt,
        LocalDateTime closeAt,
        LocalDateTime publishAt,
        @Schema(description = "배치 실행 시각. 없으면 미실행") LocalDateTime executedAt,
        @Schema(description = "발표 확정 시각. 없으면 미발표") LocalDateTime publishedAt
) {

    public static AdminMatchRoundResponse from(MatchRound round) {
        return AdminMatchRoundResponse.builder()
                .id(round.getId())
                .seq(round.getSeq())
                .status(round.getStatus())
                .openAt(round.getOpenAt())
                .closeAt(round.getCloseAt())
                .publishAt(round.getPublishAt())
                .executedAt(round.getExecutedAt())
                .publishedAt(round.getPublishedAt())
                .build();
    }
}
