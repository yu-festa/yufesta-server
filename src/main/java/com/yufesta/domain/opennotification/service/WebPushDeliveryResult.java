package com.yufesta.domain.opennotification.service;

/** Push 서비스 HTTP 응답을 상태 전이 판단에 필요한 값으로만 축약 */
public record WebPushDeliveryResult(int statusCode) {

    public boolean isAccepted() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isExpiredSubscription() {
        return statusCode == 404 || statusCode == 410;
    }
}
