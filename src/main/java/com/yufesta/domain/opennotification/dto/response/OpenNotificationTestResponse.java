package com.yufesta.domain.opennotification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** 운영자가 현재 브라우저 구독으로 보낸 즉시 테스트 Push 결과 */
@Builder
public record OpenNotificationTestResponse(
        @Schema(description = "Push 서비스가 요청을 수락했는지", example = "true")
        boolean accepted,
        @Schema(description = "Push 서비스 HTTP 응답 상태", example = "201")
        int statusCode
) {
}
