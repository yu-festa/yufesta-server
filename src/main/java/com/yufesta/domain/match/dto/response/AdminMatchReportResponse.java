package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.enums.BlockDecision;
import com.yufesta.domain.match.enums.BlockReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 운영자 검토 목록 항목. 운영자 API라 회원 id를 포함한다(공개 응답에는 금지)
 */
@Builder
public record AdminMatchReportResponse(
        @Schema(description = "신고 ID(검토 API 경로에 사용)", example = "5") Long id,
        @Schema(description = "신고 발생 회차 번호. 회차 행이 지워졌으면 null", example = "1") Integer roundSeq,
        Long reporterUserId,
        Long targetUserId,
        BlockReason reason,
        String detail,
        LocalDateTime createdAt,
        @Schema(description = "검토 시각. 없으면 미검토") LocalDateTime reviewedAt,
        @Schema(description = "검토 결과. 없으면 미검토") BlockDecision decision,
        @Schema(description = "대상의 유효 신고 수(기각 제외). 이 값이 임계 이상이면 자동 제재", example = "2") long targetReportCount,
        @Schema(description = "대상이 지금 매칭 차단 상태인지", example = "true") boolean targetBlocked
) {

    public static AdminMatchReportResponse of(Block block, long targetReportCount) {
        return AdminMatchReportResponse.builder()
                .id(block.getId())
                .roundSeq(block.getRound() == null ? null : block.getRound().getSeq())
                .reporterUserId(block.getReporter().getId())
                .targetUserId(block.getTarget().getId())
                .reason(block.getReason())
                .detail(block.getDetail())
                .createdAt(block.getCreatedAt())
                .reviewedAt(block.getReviewedAt())
                .decision(block.getDecision())
                .targetReportCount(targetReportCount)
                .targetBlocked(block.getTarget().isMatchingBlocked())
                .build();
    }
}
