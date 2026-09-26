package com.yufesta.domain.opennotification.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationTestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 운영자용 서비스 오픈 Web Push 수신 검증 API 명세 */
@Tag(name = "Admin Open notification", description = "서비스 오픈 알림 운영자 테스트")
@RequestMapping("/api/v1/admin/open-notifications")
@SecurityRequirement(name = "cookieAuth")
public interface AdminOpenNotificationApi {

    @Operation(summary = "즉시 테스트 푸시 발송", description = "STAFF 또는 OWNER만 호출할 수 있다. 현재 브라우저 PushSubscription에 실제 오픈 알림 payload를 즉시 보낸다. 구독을 저장하거나 예약 발송 상태를 바꾸지 않는다. X-XSRF-TOKEN 헤더가 필요하다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "502", description = "OPEN_NOTIFICATION_TEST_DELIVERY_FAILED")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "OPEN_NOTIFICATION_NOT_CONFIGURED")
    @PostMapping("/test-push")
    ApiResponse<OpenNotificationTestResponse> sendTestPush(
            @Valid @RequestBody CreateOpenNotificationSubscriptionRequest request
    );
}
