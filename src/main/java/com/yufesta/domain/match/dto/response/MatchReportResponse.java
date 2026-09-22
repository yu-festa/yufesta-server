package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.enums.BlockReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 신고 접수 결과. 대상 식별 정보는 넣지 않는다(NFR-SC-07)
 */
@Builder
public record MatchReportResponse(
        @Schema(description = "신고 ID", example = "5") Long id,
        @Schema(description = "신고한 결과 카드의 matchId", example = "77") Long matchId,
        BlockReason reason,
        LocalDateTime createdAt
) {

    public static MatchReportResponse of(Block block, Long matchId) {
        return MatchReportResponse.builder()
                .id(block.getId())
                .matchId(matchId)
                .reason(block.getReason())
                .createdAt(block.getCreatedAt())
                .build();
    }
}
