package com.yufesta.domain.timetable.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.timetable.controller.api.AdminTimetableApi;
import com.yufesta.domain.timetable.dto.request.ChangeSlotTimesRequest;
import com.yufesta.domain.timetable.dto.request.CreateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.request.DelaySlotRequest;
import com.yufesta.domain.timetable.dto.request.LiveSlotRequest;
import com.yufesta.domain.timetable.dto.request.ReorderSlotsRequest;
import com.yufesta.domain.timetable.dto.request.UpdateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.response.AdminTimetableSlotResponse;
import com.yufesta.domain.timetable.service.TimetableAdminService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 타임테이블 관리 API. 명세는 AdminTimetableApi */
@RestController
public class AdminTimetableController implements AdminTimetableApi {

    private final TimetableAdminService timetableAdminService;

    public AdminTimetableController(TimetableAdminService timetableAdminService) {
        this.timetableAdminService = timetableAdminService;
    }

    @Override
    public ApiResponse<List<AdminTimetableSlotResponse>> getSlots() {
        return ApiResponse.success(timetableAdminService.getSlots());
    }

    @Override
    public ResponseEntity<ApiResponse<AdminTimetableSlotResponse>> create(CreateTimetableSlotRequest request) {
        AdminTimetableSlotResponse response = timetableAdminService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "공연을 등록했습니다.", response));
    }

    @Override
    public ApiResponse<AdminTimetableSlotResponse> update(Long slotId, UpdateTimetableSlotRequest request) {
        return ApiResponse.success("공연을 수정했습니다.", timetableAdminService.update(slotId, request));
    }

    @Override
    public void delete(Long slotId) {
        timetableAdminService.delete(slotId);
    }

    @Override
    public ApiResponse<AdminTimetableSlotResponse> changeTimes(Long slotId, ChangeSlotTimesRequest request) {
        return ApiResponse.success("공연 시각을 변경했습니다.", timetableAdminService.changeTimes(slotId, request));
    }

    @Override
    public ApiResponse<AdminTimetableSlotResponse> setDelay(Long slotId, DelaySlotRequest request) {
        return ApiResponse.success("지연을 반영했습니다.", timetableAdminService.setDelay(slotId, request));
    }

    @Override
    public ApiResponse<AdminTimetableSlotResponse> setLive(Long slotId, LiveSlotRequest request) {
        return ApiResponse.success("진행 중 표시를 바꿨습니다.", timetableAdminService.setLive(slotId, request));
    }

    @Override
    public ApiResponse<List<AdminTimetableSlotResponse>> reorder(ReorderSlotsRequest request) {
        return ApiResponse.success("공연 순서를 바꿨습니다.", timetableAdminService.reorder(request));
    }
}
