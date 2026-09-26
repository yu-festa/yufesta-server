package com.yufesta.domain.opennotification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 브라우저 PushSubscription을 서버에 등록하는 요청 */
@Builder
public record CreateOpenNotificationSubscriptionRequest(
        @Schema(description = "브라우저 PushSubscription endpoint", example = "https://fcm.googleapis.com/fcm/send/example")
        @NotBlank @Size(max = 500) String endpoint,
        @Schema(description = "브라우저가 생성한 payload 암호화 키") @NotNull @Valid Keys keys
) {

    /** Web Push 표준 PushSubscription.keys 객체 */
    @Builder
    public record Keys(
            @Schema(description = "P-256 ECDH 공개키") @NotBlank @Size(max = 255) String p256dh,
            @Schema(description = "인증 비밀값") @NotBlank @Size(max = 255) String auth
    ) {
    }
}
