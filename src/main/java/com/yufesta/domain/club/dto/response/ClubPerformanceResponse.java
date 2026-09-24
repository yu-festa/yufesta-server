package com.yufesta.domain.club.dto.response;

import com.yufesta.domain.timetable.entity.TimetableSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/** 라인업 카드에 붙는 공연 시간·무대(FR-LU-01). slotId로 타임테이블 항목, stagePlaceId로 지도 핀(FR-LU-05)에 연결한다 */
@Builder
public record ClubPerformanceResponse(
        @Schema(description = "타임테이블 슬롯 ID", example = "4") Long slotId,
        @Schema(description = "공연명", example = "HIPCOM") String title,
        @Schema(description = "원래 시작 시각", example = "2026-10-02T16:15:00") LocalDateTime startAt,
        @Schema(description = "지연을 반영한 실제 시작 시각", example = "2026-10-02T16:15:00") LocalDateTime effectiveStartAt,
        @Schema(description = "원래 종료 시각", example = "2026-10-02T16:45:00") LocalDateTime endAt,
        @Schema(description = "무대 장소 ID", example = "1") Long stagePlaceId,
        @Schema(description = "무대 이름", example = "중앙 무대") String stageName
) {

    public static ClubPerformanceResponse from(TimetableSlot slot) {
        return ClubPerformanceResponse.builder()
                .slotId(slot.getId())
                .title(slot.getTitle())
                .startAt(slot.getStartAt())
                .effectiveStartAt(slot.getEffectiveStartAt())
                .endAt(slot.getEndAt())
                .stagePlaceId(slot.getStage().getId())
                .stageName(slot.getStage().getName())
                .build();
    }
}
