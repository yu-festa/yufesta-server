package com.yufesta.domain.opennotification.service;

/** VAPID 인증으로 브라우저 Push endpoint에 payload를 보내는 어댑터 */
public interface WebPushSender {

    WebPushDeliveryResult send(OpenNotificationDispatch dispatch, OpenNotificationPayload payload);
}
