package com.yufesta.domain.opennotification;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 서비스 오픈 Web Push의 VAPID 키와 예약 발송 정책 설정 */
@ConfigurationProperties(prefix = "app.open-notification")
public record OpenNotificationProperties(
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject,
        OffsetDateTime openAt,
        int maxAttempts,
        Duration retryBaseDelay,
        Duration deliveryLease,
        int batchSize
) {

    /** VAPID 공개키·개인키·연락처가 모두 주입됐는지 확인한다. */
    public boolean isVapidConfigured() {
        return StringUtils.hasText(vapidPublicKey)
                && StringUtils.hasText(vapidPrivateKey)
                && StringUtils.hasText(vapidSubject);
    }
}
