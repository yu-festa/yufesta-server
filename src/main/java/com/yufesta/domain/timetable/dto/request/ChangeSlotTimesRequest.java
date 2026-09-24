package com.yufesta.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Builder;

/** 공연 시각 변경 요청. 시작 시각이 바뀌면 최초 1회 원래 시각을 기록한다(FR-TT-04) */
@Builder
public record ChangeSlotTimesRequest(
        @Schema(description = "새 시작 시각(KST)", example = "2026-10-02T16:30:00") @NotNull LocalDateTime startAt,
        @Schema(description = "새 종료 시각(KST). 시작보다 뒤", example = "2026-10-02T17:00:00") @NotNull LocalDateTime endAt
) {
}
