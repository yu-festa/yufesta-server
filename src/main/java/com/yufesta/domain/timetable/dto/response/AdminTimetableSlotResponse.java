package com.yufesta.domain.timetable.dto.response;

import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/** 운영자용 공연 응답. 계산값 대신 저장된 원본 컬럼을 그대로 보여준다 */
@Builder
public record AdminTimetableSlotResponse(
        Long id,
        int sortOrder,
        String title,
        SlotType slotType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        @Schema(description = "변경 전 원래 시작 시각. 없으면 null") LocalDateTime changedFromStart,
        @Schema(description = "지연 분. 없으면 null") Integer delayMinutes,
        @Schema(description = "운영자 수동 진행 중 지정 여부") boolean liveOverride,
        Long stagePlaceId,
        String stageName,
        Long clubId
) {

    public static AdminTimetableSlotResponse from(TimetableSlot slot) {
        return AdminTimetableSlotResponse.builder()
                .id(slot.getId())
                .sortOrder(slot.getSortOrder())
                .title(slot.getTitle())
                .slotType(slot.getSlotType())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .changedFromStart(slot.getChangedFromStart())
                .delayMinutes(slot.getDelayMinutes())
                .liveOverride(slot.isLiveOverride())
                .stagePlaceId(slot.getStage().getId())
                .stageName(slot.getStage().getName())
                .clubId(slot.getClubId())
                .build();
    }
}
