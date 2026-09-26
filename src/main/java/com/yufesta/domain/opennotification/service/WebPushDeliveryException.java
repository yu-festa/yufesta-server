package com.yufesta.domain.opennotification.service;

/** Web Push payload 암호화 또는 네트워크 전송 실패 */
public class WebPushDeliveryException extends RuntimeException {

    public WebPushDeliveryException(Throwable cause) {
        super(cause);
    }
}
