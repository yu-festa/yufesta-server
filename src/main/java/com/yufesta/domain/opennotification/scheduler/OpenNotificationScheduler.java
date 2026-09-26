package com.yufesta.domain.opennotification.scheduler;

import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.service.OpenNotificationDeliveryService;
import com.yufesta.domain.opennotification.service.OpenNotificationDispatch;
import com.yufesta.domain.opennotification.service.OpenNotificationPayload;
import com.yufesta.domain.opennotification.service.WebPushDeliveryException;
import com.yufesta.domain.opennotification.service.WebPushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서비스 오픈 시각 이후 DB lease를 획득한 구독에 한 번만 Web Push를 발송한다. */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OpenNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(OpenNotificationScheduler.class);

    private final OpenNotificationDeliveryService deliveryService;
    private final WebPushSender webPushSender;
    private final OpenNotificationProperties properties;

    public OpenNotificationScheduler(
            OpenNotificationDeliveryService deliveryService,
            WebPushSender webPushSender,
            OpenNotificationProperties properties
    ) {
        this.deliveryService = deliveryService;
        this.webPushSender = webPushSender;
        this.properties = properties;
    }

    /** 설정된 batchSize만큼 lease를 얻어 발송한다. VAPID 키가 빠졌으면 구독 상태를 바꾸지 않고 운영 로그만 남긴다. */
    @Scheduled(fixedDelayString = "${app.scheduler.open-notification-tick:PT30S}")
    public void tick() {
        if (!properties.isVapidConfigured()) {
            // 로컬 개발 등 키가 없는 환경에서는 구독 상태를 바꾸거나 주기 로그를 쌓지 않는다.
            return;
        }
        for (int count = 0; count < properties.batchSize(); count++) {
            OpenNotificationDispatch dispatch = deliveryService.claimNext().orElse(null);
            if (dispatch == null) {
                return;
            }
            deliver(dispatch);
        }
    }

    private void deliver(OpenNotificationDispatch dispatch) {
        try {
            deliveryService.recordResult(dispatch.id(), webPushSender.send(dispatch, OpenNotificationPayload.festivalOpen()));
        } catch (WebPushDeliveryException exception) {
            // endpoint·키 원문은 capability 정보라 로그에 남기지 않는다.
            log.info("서비스 오픈 알림 발송이 일시 실패했습니다. subscriptionId={}", dispatch.id());
            deliveryService.recordTemporaryFailure(dispatch.id());
        } catch (RuntimeException exception) {
            log.error("서비스 오픈 알림 처리 중 예기치 않은 오류가 발생했습니다. subscriptionId={}", dispatch.id(), exception);
            deliveryService.recordTemporaryFailure(dispatch.id());
        }
    }
}
