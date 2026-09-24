package com.yufesta.domain.timetable.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.timetable.controller.api.TimetableApi;
import com.yufesta.domain.timetable.dto.response.TimetableResponse;
import com.yufesta.domain.timetable.service.TimetableService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공연 타임테이블 공개 API. 명세는 TimetableApi
 */
@RestController
public class TimetableController implements TimetableApi {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @Override
    public ApiResponse<TimetableResponse> getTimetable() {
        return ApiResponse.success(timetableService.getTimetable());
    }
}
