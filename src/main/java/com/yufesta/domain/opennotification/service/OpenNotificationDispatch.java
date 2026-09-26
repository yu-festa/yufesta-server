package com.yufesta.domain.opennotification.service;

/** lease를 획득한 Web Push 발송에만 필요한 구독 정보 */
public record OpenNotificationDispatch(Long id, String endpoint, String p256dhKey, String authKey) {
}
