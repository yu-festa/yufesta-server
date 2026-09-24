package com.yufesta.domain.timetable.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Builder;

/** 공연 순서 일괄 변경 요청. 모든 공연 ID를 원하는 순서로 한 번씩 담는다 */
@Builder
public record ReorderSlotsRequest(
        @Schema(description = "표시 순서대로 나열한 공연 ID 전체", example = "[1, 3, 2, 4]") @NotEmpty List<Long> slotIds
) {
}
