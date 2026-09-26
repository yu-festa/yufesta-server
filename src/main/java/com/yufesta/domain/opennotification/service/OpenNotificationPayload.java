package com.yufesta.domain.opennotification.service;

/** 서비스 워커가 표시할 서비스 오픈 알림 payload */
public record OpenNotificationPayload(String type, String title, String body, String url) {

    public static OpenNotificationPayload festivalOpen() {
        return new OpenNotificationPayload(
                "FESTIVAL_OPEN",
                "YU FESTA가 오픈했어요!",
                "공연 시간표부터 축제 지도까지, 지금 확인해보세요.",
                "/main"
        );
    }
}
