package com.yufesta.domain.opennotification.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.opennotification.dto.request.CancelOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationSubscriptionResponse;
import com.yufesta.domain.opennotification.dto.response.VapidPublicKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 랜딩 페이지 서비스 오픈 Web Push 공개 API 명세 */
@Tag(name = "Open notification", description = "서비스 오픈 알림 Web Push (#110)")
@RequestMapping("/api/v1/open-notifications")
public interface OpenNotificationApi {

    @Operation(summary = "VAPID 공개키 조회", description = "로그인 없이 브라우저 PushManager.subscribe에 쓸 공개키를 조회한다. private key는 노출하지 않는다. #110")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "OPEN_NOTIFICATION_NOT_CONFIGURED")
    @GetMapping("/vapid-public-key")
    ApiResponse<VapidPublicKeyResponse> getVapidPublicKey();

    @Operation(summary = "서비스 오픈 알림 구독", description = "로그인 없이 브라우저 PushSubscription을 저장한다. POST 전 GET /api/v1/auth/csrf를 호출하고 XSRF-TOKEN을 X-XSRF-TOKEN 헤더로 보낸다. 같은 endpoint는 중복 저장하지 않는다. #110")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "OPEN_NOTIFICATION_CLOSED")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "OPEN_NOTIFICATION_NOT_CONFIGURED")
    @PostMapping("/subscriptions")
    ResponseEntity<ApiResponse<OpenNotificationSubscriptionResponse>> subscribe(
            @Valid @RequestBody CreateOpenNotificationSubscriptionRequest request
    );

    @Operation(summary = "서비스 오픈 알림 구독 취소", description = "로그인 없이 해당 브라우저 endpoint를 취소한다. DELETE 전 GET /api/v1/auth/csrf를 호출하고 XSRF-TOKEN을 X-XSRF-TOKEN 헤더로 보낸다. 이미 없는 endpoint도 204로 처리한다. #110")
    @DeleteMapping("/subscriptions")
    ResponseEntity<Void> cancel(
            @Valid @RequestBody CancelOpenNotificationSubscriptionRequest request
    );
}
