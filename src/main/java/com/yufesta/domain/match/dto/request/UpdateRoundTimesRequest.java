package com.yufesta.domain.match.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 회차 시각 수정(FR-MT-01). 규칙 검증(close = publish − 10분, open < close)은 서비스에서 한다
 */
@Builder
public record UpdateRoundTimesRequest(
        @Schema(description = "접수 시작", example = "2026-09-25T00:00:00") @NotNull LocalDateTime openAt,
        @Schema(description = "접수 마감. 발표 10분 전이어야 한다", example = "2026-10-02T15:50:00") @NotNull LocalDateTime closeAt,
        @Schema(description = "발표 시각", example = "2026-10-02T16:00:00") @NotNull LocalDateTime publishAt
) {
}
