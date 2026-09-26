package com.yufesta.domain.opennotification.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.opennotification.controller.api.OpenNotificationApi;
import com.yufesta.domain.opennotification.dto.request.CancelOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationSubscriptionResponse;
import com.yufesta.domain.opennotification.dto.response.VapidPublicKeyResponse;
import com.yufesta.domain.opennotification.service.OpenNotificationSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 오픈 알림 공개 API. 명세는 OpenNotificationApi */
@RestController
public class OpenNotificationController implements OpenNotificationApi {

    private final OpenNotificationSubscriptionService subscriptionService;

    public OpenNotificationController(OpenNotificationSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public ApiResponse<VapidPublicKeyResponse> getVapidPublicKey() {
        return ApiResponse.success(subscriptionService.getVapidPublicKey());
    }

    @Override
    public ResponseEntity<ApiResponse<OpenNotificationSubscriptionResponse>> subscribe(
            CreateOpenNotificationSubscriptionRequest request
    ) {
        OpenNotificationSubscriptionResponse subscription = subscriptionService.subscribe(request);
        HttpStatus status = subscription.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(ApiResponse.of(status, "서비스 오픈 알림을 신청했습니다.", subscription));
    }

    @Override
    public ResponseEntity<Void> cancel(CancelOpenNotificationSubscriptionRequest request) {
        subscriptionService.cancel(request);
        return ResponseEntity.noContent().build();
    }
}
