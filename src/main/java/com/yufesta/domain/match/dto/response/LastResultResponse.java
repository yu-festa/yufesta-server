package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.enums.MatchResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 가장 최근 발표된 회차에서의 내 결과 요약. 상세 카드는 결과 API(FR-MT-32)
 */
@Builder
public record LastResultResponse(
        @Schema(description = "발표된 회차 번호", example = "1") int roundSeq,
        @Schema(description = "매칭 여부", example = "MATCHED") MatchResultStatus status
) {

    public static LastResultResponse of(int roundSeq, MatchResultStatus status) {
        return LastResultResponse.builder()
                .roundSeq(roundSeq)
                .status(status)
                .build();
    }
}
