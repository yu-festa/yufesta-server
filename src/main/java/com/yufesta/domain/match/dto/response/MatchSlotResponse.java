package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/** 인스타팅에서 보는 공연 한 건(FR-MT-05·32). 선택지 목록과 신청·상대 카드의 "보고 싶은 공연"에 같은 형태를 쓴다 */
@Builder
public record MatchSlotResponse(
        @Schema(description = "공연 슬롯 ID", example = "4") Long id,
        @Schema(description = "공연명", example = "HIPCOM") String title,
        @Schema(description = "구분", example = "CLUB") SlotType slotType,
        @Schema(description = "시작 시각(원래 시각)", example = "2026-10-02T16:15:00") LocalDateTime startAt,
        @Schema(description = "종료 시각", example = "2026-10-02T16:45:00") LocalDateTime endAt,
        @Schema(description = "무대 장소 ID", example = "1") Long stagePlaceId,
        @Schema(description = "무대 이름", example = "중앙 무대") String stageName
) {

    public static MatchSlotResponse from(TimetableSlot slot) {
        return MatchSlotResponse.builder()
                .id(slot.getId())
                .title(slot.getTitle())
                .slotType(slot.getSlotType())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .stagePlaceId(slot.getStage().getId())
                .stageName(slot.getStage().getName())
                .build();
    }
}
