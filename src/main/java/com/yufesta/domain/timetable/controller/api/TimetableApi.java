package com.yufesta.domain.timetable.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.timetable.dto.response.TimetableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 공연 타임테이블 공개 API 명세
 */
@Tag(name = "Timetable", description = "공연 타임테이블 (FR-TT)")
@RequestMapping("/api/v1/timetable")
public interface TimetableApi {

    @Operation(
            summary = "타임테이블",
            description = "공연 목록을 표시 순서대로 준다. serverNow와 effectiveStartAt(지연 반영)으로 카운트다운·다음 공연을 계산한다. "
                    + "isLive는 운영자 수동 지정이 있으면 그 항목만, 없으면 서버 시계 기준. isChanged면 changedFromStart를 취소선으로 보여준다. "
                    + "로그인 불필요. FR-TT-01~04"
    )
    @GetMapping
    ApiResponse<TimetableResponse> getTimetable();
}
