package com.yufesta.domain.opennotification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 브라우저 Web Push 구독을 취소하는 요청 */
@Builder
public record CancelOpenNotificationSubscriptionRequest(
        @Schema(description = "취소할 브라우저 PushSubscription endpoint", example = "https://fcm.googleapis.com/fcm/send/example")
        @NotBlank @Size(max = 500) String endpoint
) {
}
