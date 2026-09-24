package com.yufesta.domain.timetable.dto.response;

import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 타임테이블 항목. 원래 시각(startAt)과 지연을 반영한 실제 시각(effectiveStartAt)을 둘 다 준다
 */
@Builder
public record TimetableSlotResponse(
        @Schema(description = "슬롯 ID. 인스타팅 '보고 싶은 공연' 선택값", example = "4") Long id,
        @Schema(description = "표시 순서", example = "4") int sortOrder,
        @Schema(description = "공연명(출연자명)", example = "HIPCOM") String title,
        @Schema(description = "구분. CLUB·GUEST는 공연, EVENT는 개회식·연설 같은 순서", example = "CLUB") SlotType slotType,
        @Schema(description = "원래 시작 시각", example = "2026-10-02T16:15:00") LocalDateTime startAt,
        @Schema(description = "원래 종료 시각", example = "2026-10-02T16:45:00") LocalDateTime endAt,
        @Schema(description = "지연을 반영한 실제 시작 시각. 카운트다운·다음 공연 기준", example = "2026-10-02T16:25:00") LocalDateTime effectiveStartAt,
        @Schema(description = "지연을 반영한 실제 종료 시각", example = "2026-10-02T16:55:00") LocalDateTime effectiveEndAt,
        @Schema(description = "운영자가 넣은 지연 분. 없으면 null", example = "10") Integer delayMinutes,
        @Schema(description = "진행 중 여부. 운영자 수동 지정이 있으면 그 항목만, 없으면 서버 시계 기준", example = "true") boolean isLive,
        @Schema(description = "순서·시간 변경 여부. true면 changedFromStart를 취소선으로 표시", example = "false") boolean isChanged,
        @Schema(description = "변경 전 원래 시작 시각. 변경이 없으면 null") LocalDateTime changedFromStart,
        @Schema(description = "무대") TimetableStageResponse stage,
        @Schema(description = "출연 동아리. EVENT·GUEST는 null") TimetableClubResponse club
) {

    public static TimetableSlotResponse of(TimetableSlot slot, boolean live) {
        return TimetableSlotResponse.builder()
                .id(slot.getId())
                .sortOrder(slot.getSortOrder())
                .title(slot.getTitle())
                .slotType(slot.getSlotType())
                .startAt(slot.getStartAt())
                .endAt(slot.getEndAt())
                .effectiveStartAt(slot.getEffectiveStartAt())
                .effectiveEndAt(slot.getEffectiveEndAt())
                .delayMinutes(slot.getDelayMinutes())
                .isLive(live)
                .isChanged(slot.isChanged())
                .changedFromStart(slot.getChangedFromStart())
                .stage(TimetableStageResponse.from(slot.getStage()))
                .club(slot.getClub() == null ? null : TimetableClubResponse.from(slot.getClub()))
                .build();
    }
}
