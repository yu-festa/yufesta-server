package com.yufesta.domain.opennotification.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.opennotification.controller.api.AdminOpenNotificationApi;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationTestResponse;
import com.yufesta.domain.opennotification.service.OpenNotificationTestService;
import org.springframework.web.bind.annotation.RestController;

/** 운영자용 서비스 오픈 Web Push 수신 검증 API. 명세는 AdminOpenNotificationApi */
@RestController
public class AdminOpenNotificationController implements AdminOpenNotificationApi {

    private final OpenNotificationTestService openNotificationTestService;

    public AdminOpenNotificationController(OpenNotificationTestService openNotificationTestService) {
        this.openNotificationTestService = openNotificationTestService;
    }

    @Override
    public ApiResponse<OpenNotificationTestResponse> sendTestPush(CreateOpenNotificationSubscriptionRequest request) {
        return ApiResponse.success("테스트 푸시를 전송했습니다.", openNotificationTestService.sendTest(request));
    }
}
