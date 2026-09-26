package com.yufesta.domain.opennotification.dto.response;

import lombok.Builder;

/** 브라우저 PushManager.subscribe에 전달할 VAPID 공개키 응답 */
@Builder
public record VapidPublicKeyResponse(String publicKey) {
}
