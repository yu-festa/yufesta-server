package com.yufesta.domain.cheer.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.cheer.controller.api.AdminCheerApi;
import com.yufesta.domain.cheer.dto.request.UpdateCheerVisibilityRequest;
import com.yufesta.domain.cheer.dto.response.AdminCheerResponse;
import com.yufesta.domain.cheer.service.CheerService;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 응원 메시지 관리 API. 명세는 AdminCheerApi */
@RestController
public class AdminCheerController implements AdminCheerApi {

    private final CheerService cheerService;

    public AdminCheerController(CheerService cheerService) {
        this.cheerService = cheerService;
    }

    @Override
    public ApiResponse<AdminCheerResponse> updateVisibility(
            Long cheerId,
            UpdateCheerVisibilityRequest request
    ) {
        AdminCheerResponse cheer = cheerService.updateVisibility(cheerId, request);
        String message = request.hidden() ? "응원 메시지를 숨겼습니다." : "응원 메시지를 복구했습니다.";
        return ApiResponse.success(message, cheer);
    }
}
