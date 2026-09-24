package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/** 현재 회차에서 고를 수 있는 공연 목록(FR-MT-05). 신청 화면은 이 목록만 보여준다 */
@Builder
public record MatchSlotsResponse(
        @Schema(description = "기준 회차 번호", example = "1") int roundSeq,
        @Schema(description = "기준 발표 시각. 이 시각 이후 시작 공연만 담긴다", example = "2026-10-02T16:00:00") LocalDateTime publishAt,
        @Schema(description = "선택 가능한 공연. 표시 순서대로") List<MatchSlotResponse> slots
) {

    public static MatchSlotsResponse of(MatchRound round, List<TimetableSlot> slots) {
        return MatchSlotsResponse.builder()
                .roundSeq(round.getSeq())
                .publishAt(round.getPublishAt())
                .slots(slots.stream().map(MatchSlotResponse::from).toList())
                .build();
    }
}
