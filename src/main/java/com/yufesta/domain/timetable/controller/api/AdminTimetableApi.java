package com.yufesta.domain.timetable.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.timetable.dto.request.ChangeSlotTimesRequest;
import com.yufesta.domain.timetable.dto.request.CreateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.request.DelaySlotRequest;
import com.yufesta.domain.timetable.dto.request.LiveSlotRequest;
import com.yufesta.domain.timetable.dto.request.ReorderSlotsRequest;
import com.yufesta.domain.timetable.dto.request.UpdateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.response.AdminTimetableSlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 운영자 타임테이블 관리 API 명세 */
@Tag(name = "Admin Timetable", description = "운영자 타임테이블 관리 (FR-ADM-06)")
@RequestMapping("/api/v1/admin/timetable")
@SecurityRequirement(name = "cookieAuth")
public interface AdminTimetableApi {

    @Operation(summary = "공연 목록(운영자)", description = "저장된 원본 컬럼 그대로. 표시 순서대로. STAFF")
    @GetMapping
    ApiResponse<List<AdminTimetableSlotResponse>> getSlots();

    @Operation(summary = "공연 등록", description = "무대는 category=STAGE 장소만. 종료는 시작보다 뒤. STAFF, X-XSRF-TOKEN 헤더 필요. FR-TT-01")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "TIMETABLE_INVALID_TIME, TIMETABLE_STAGE_INVALID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND")
    @PostMapping
    ResponseEntity<ApiResponse<AdminTimetableSlotResponse>> create(@Valid @RequestBody CreateTimetableSlotRequest request);

    @Operation(summary = "공연 수정", description = "공연명·구분·무대·동아리 연결. 시각은 times API로. STAFF, X-XSRF-TOKEN 헤더 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "TIMETABLE_STAGE_INVALID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "TIMETABLE_SLOT_NOT_FOUND, PLACE_NOT_FOUND")
    @PatchMapping("/{slotId}")
    ApiResponse<AdminTimetableSlotResponse> update(
            @PathVariable Long slotId,
            @Valid @RequestBody UpdateTimetableSlotRequest request
    );

    @Operation(summary = "공연 삭제", description = "이 공연을 고른 인스타팅 신청의 선택값은 비워진다. STAFF, X-XSRF-TOKEN 헤더 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "TIMETABLE_SLOT_NOT_FOUND")
    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long slotId);

    @Operation(
            summary = "공연 시각 변경",
            description = "시작 시각이 처음 바뀔 때만 원래 시각(changedFromStart)이 기록돼 화면에 '순서 변경됨'과 취소선이 붙는다. "
                    + "STAFF, X-XSRF-TOKEN 헤더 필요. FR-TT-04"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "TIMETABLE_INVALID_TIME")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "TIMETABLE_SLOT_NOT_FOUND")
    @PatchMapping("/{slotId}/times")
    ApiResponse<AdminTimetableSlotResponse> changeTimes(
            @PathVariable Long slotId,
            @Valid @RequestBody ChangeSlotTimesRequest request
    );

    @Operation(summary = "공연 지연 표시", description = "지연 분을 넣으면 실제 시각(effectiveStartAt)이 밀리고 원래 시각은 남는다. null이면 해제. STAFF, X-XSRF-TOKEN 헤더 필요. FR-TT-03")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "TIMETABLE_SLOT_NOT_FOUND")
    @PatchMapping("/{slotId}/delay")
    ApiResponse<AdminTimetableSlotResponse> setDelay(
            @PathVariable Long slotId,
            @Valid @RequestBody DelaySlotRequest request
    );

    @Operation(summary = "진행 중 수동 지정", description = "true면 이 공연만 진행 중(다른 공연 지정 해제), false면 해제. 지정이 하나라도 있으면 시계 판정을 무시한다. STAFF, X-XSRF-TOKEN 헤더 필요. FR-TT-03")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "TIMETABLE_SLOT_NOT_FOUND")
    @PatchMapping("/{slotId}/live")
    ApiResponse<AdminTimetableSlotResponse> setLive(
            @PathVariable Long slotId,
            @Valid @RequestBody LiveSlotRequest request
    );

    @Operation(summary = "공연 순서 일괄 변경", description = "모든 공연 ID를 원하는 순서로 한 번씩 보낸다. 나열 순서대로 1부터 다시 매긴다. STAFF, X-XSRF-TOKEN 헤더 필요. FR-TT-04")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "TIMETABLE_ORDER_INVALID")
    @PutMapping("/order")
    ApiResponse<List<AdminTimetableSlotResponse>> reorder(@Valid @RequestBody ReorderSlotsRequest request);
}
