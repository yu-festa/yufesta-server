package com.yufesta.domain.match.dto.request;

import com.yufesta.domain.match.enums.BlockReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * 매칭 상대 신고(FR-MT-40). 대상은 userId가 아니라 결과 카드의 matchId로 지정한다(공개 응답에 userId가 없다)
 */
@Builder
public record ReportMatchRequest(
        @Schema(description = "결과 카드의 matchId", example = "77")
        @NotNull Long matchId,

        @Schema(description = "사유. PROFILE 불쾌한 프로필, FAKE 허위 정보·성별 위조, OTHER 기타", example = "FAKE")
        @NotNull BlockReason reason,

        @Schema(description = "상세(선택, 500자 이내)", example = "프로필과 다른 사람이었어요")
        @Size(max = 500) String detail
) {
}
