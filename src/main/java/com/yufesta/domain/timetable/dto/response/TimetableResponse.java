package com.yufesta.domain.timetable.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * 타임테이블 화면 응답. 홈의 진행 중·다음 공연은 프론트가 serverNow와 이 목록으로 계산한다(FR-TT-02)
 */
@Builder
public record TimetableResponse(
        @Schema(description = "서버 기준 현재 시각(KST). LIVE·다음 공연 계산의 기준", example = "2026-10-02T16:20:00") LocalDateTime serverNow,
        @Schema(description = "공연 목록. 표시 순서대로") List<TimetableSlotResponse> slots
) {

    public static TimetableResponse of(LocalDateTime serverNow, List<TimetableSlotResponse> slots) {
        return TimetableResponse.builder()
                .serverNow(serverNow)
                .slots(slots)
                .build();
    }
}
