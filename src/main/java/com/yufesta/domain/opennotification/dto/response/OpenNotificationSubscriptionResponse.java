package com.yufesta.domain.opennotification.dto.response;

import java.time.OffsetDateTime;
import lombok.Builder;

/** 서비스 오픈 알림 구독 등록 결과 */
@Builder
public record OpenNotificationSubscriptionResponse(
        boolean subscribed,
        boolean duplicate,
        OffsetDateTime scheduledFor
) {
}
