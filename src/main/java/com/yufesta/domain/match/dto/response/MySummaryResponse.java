package com.yufesta.domain.match.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 로그인 사용자의 인스타팅 상태(FR-MT-51·54). 비로그인이면 summary에서 null
 */
@Builder
public record MySummaryResponse(
        @Schema(description = "현재 회차에 유효한 신청이 있는지", example = "true") boolean applied,
        @Schema(description = "최근 발표된 회차의 내 결과. 신청하지 않았거나 발표 전이면 null") LastResultResponse lastResult
) {

    public static MySummaryResponse of(boolean applied, LastResultResponse lastResult) {
        return MySummaryResponse.builder()
                .applied(applied)
                .lastResult(lastResult)
                .build();
    }
}
