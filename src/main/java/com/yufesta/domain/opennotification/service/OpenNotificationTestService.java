package com.yufesta.domain.opennotification.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationTestResponse;
import org.springframework.stereotype.Service;

/** 운영자가 배포 환경에서 실제 Web Push 수신만 즉시 확인하는 용도의 서비스 */
@Service
public class OpenNotificationTestService {

    private final OpenNotificationProperties properties;
    private final WebPushSender webPushSender;

    public OpenNotificationTestService(OpenNotificationProperties properties, WebPushSender webPushSender) {
        this.properties = properties;
        this.webPushSender = webPushSender;
    }

    /**
     * 구독을 저장하거나 예약 발송 상태를 바꾸지 않고, 요청한 브라우저에만 즉시 Push를 전송한다.
     * 실제 서비스 오픈 알림과 같은 payload를 보내 service worker의 표시·클릭 동작까지 검증한다.
     */
    public OpenNotificationTestResponse sendTest(CreateOpenNotificationSubscriptionRequest request) {
        requireVapidConfigured();

        WebPushDeliveryResult result;
        try {
            result = webPushSender.send(
                    new OpenNotificationDispatch(
                            0L,
                            request.endpoint().trim(),
                            request.keys().p256dh().trim(),
                            request.keys().auth().trim()
                    ),
                    OpenNotificationPayload.festivalOpen()
            );
        } catch (WebPushDeliveryException exception) {
            throw new CustomException(ErrorCode.OPEN_NOTIFICATION_TEST_DELIVERY_FAILED);
        }

        if (!result.isAccepted()) {
            throw new CustomException(ErrorCode.OPEN_NOTIFICATION_TEST_DELIVERY_FAILED);
        }
        return OpenNotificationTestResponse.builder()
                .accepted(true)
                .statusCode(result.statusCode())
                .build();
    }

    private void requireVapidConfigured() {
        if (!properties.isVapidConfigured()) {
            throw new CustomException(ErrorCode.OPEN_NOTIFICATION_NOT_CONFIGURED);
        }
    }
}
