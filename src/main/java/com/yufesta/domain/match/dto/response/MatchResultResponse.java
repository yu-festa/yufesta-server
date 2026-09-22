package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.enums.MatchResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

/**
 * 발표 후 내 결과(FR-MT-31·32·35·54). 발표 전에는 이 응답이 만들어지지 않는다
 */
@Builder
public record MatchResultResponse(
        @Schema(description = "결과 회차 번호", example = "1") int roundSeq,
        @Schema(description = "매칭 여부", example = "MATCHED") MatchResultStatus status,
        @Schema(description = "상대 카드. 점수 높은 순. 신고한 상대는 제외") List<PartnerCardResponse> partners,
        @Schema(description = "다음 회차 번호. 마지막 회차면 null", example = "2") Integer nextRoundSeq,
        @Schema(description = "다음 회차에 유효한 신청이 있는지(이월·재참여·직접 신청 포함)", example = "false") boolean hasNextRoundApplication,
        @Schema(description = "재참여 가능 여부: 매칭됨 + 다음 회차 접수 중 + 다음 회차 신청 없음", example = "true") boolean canRejoin
) {
}
