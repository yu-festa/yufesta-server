package com.yufesta.domain.timetable.dto.request;

import com.yufesta.domain.timetable.enums.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Builder;

/** 공연 등록 요청 */
@Builder
public record CreateTimetableSlotRequest(
        @Schema(description = "공연명(출연자명)", example = "HIPCOM") @NotBlank @Size(max = 50) String title,
        @Schema(description = "구분", example = "CLUB") @NotNull SlotType slotType,
        @Schema(description = "시작 시각(KST)", example = "2026-10-02T16:15:00") @NotNull LocalDateTime startAt,
        @Schema(description = "종료 시각(KST). 시작보다 뒤", example = "2026-10-02T16:45:00") @NotNull LocalDateTime endAt,
        @Schema(description = "무대 장소 ID(category=STAGE)", example = "1") @NotNull Long stagePlaceId,
        @Schema(description = "라인업 동아리 ID(선택)", example = "3") Long clubId,
        @Schema(description = "표시 순서", example = "4") @NotNull Integer sortOrder
) {
}
