package com.yufesta.domain.timetable.dto.request;

import com.yufesta.domain.timetable.enums.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 공연 기본 정보 수정 요청. 시각·지연·LIVE·순서는 각각 전용 API로 바꾼다 */
@Builder
public record UpdateTimetableSlotRequest(
        @Schema(description = "공연명(출연자명)", example = "HIPCOM") @NotBlank @Size(max = 50) String title,
        @Schema(description = "구분", example = "CLUB") @NotNull SlotType slotType,
        @Schema(description = "무대 장소 ID(category=STAGE)", example = "1") @NotNull Long stagePlaceId,
        @Schema(description = "라인업 동아리 ID(선택). null이면 연결 해제", example = "3") Long clubId
) {
}
