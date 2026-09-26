package com.yufesta.domain.opennotification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.dto.request.CancelOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationSubscriptionResponse;
import com.yufesta.domain.opennotification.repository.OpenNotificationSubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenNotificationSubscriptionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime OPEN_AT = OffsetDateTime.parse("2026-10-02T08:00:00+09:00");

    @Mock
    private OpenNotificationSubscriptionRepository subscriptionRepository;

    private OpenNotificationSubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new OpenNotificationSubscriptionService(
                subscriptionRepository,
                properties(),
                clockAt("2026-10-01T12:00:00+09:00")
        );
    }

    @Test
    void 새_endpoint를_등록하면_201용_중복_아님_응답을_만든다() {
        when(subscriptionRepository.existsByEndpoint("https://push.example/subscription")).thenReturn(false);
        when(subscriptionRepository.upsertActiveSubscription("https://push.example/subscription", "p256dh-key", "auth-key"))
                .thenReturn(1);

        OpenNotificationSubscriptionResponse response = subscriptionService.subscribe(request());

        assertThat(response.subscribed()).isTrue();
        assertThat(response.duplicate()).isFalse();
        assertThat(response.scheduledFor()).isEqualTo(OPEN_AT);
        verify(subscriptionRepository).upsertActiveSubscription("https://push.example/subscription", "p256dh-key", "auth-key");
    }

    @Test
    void 같은_endpoint를_재신청하면_기존_행을_갱신하고_중복_응답을_반환한다() {
        when(subscriptionRepository.existsByEndpoint("https://push.example/subscription")).thenReturn(true);
        when(subscriptionRepository.upsertActiveSubscription("https://push.example/subscription", "p256dh-key", "auth-key"))
                .thenReturn(2);

        OpenNotificationSubscriptionResponse response = subscriptionService.subscribe(request());

        assertThat(response.duplicate()).isTrue();
        verify(subscriptionRepository).upsertActiveSubscription("https://push.example/subscription", "p256dh-key", "auth-key");
    }

    @Test
    void 오픈_시각_이후_구독은_OPEN_NOTIFICATION_CLOSED를_던진다() {
        OpenNotificationSubscriptionService closedService = new OpenNotificationSubscriptionService(
                subscriptionRepository,
                properties(),
                clockAt("2026-10-02T08:00:00+09:00")
        );

        assertThatThrownBy(() -> closedService.subscribe(request()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OPEN_NOTIFICATION_CLOSED);
    }

    @Test
    void 없는_endpoint_취소는_성공으로_끝난다() {
        when(subscriptionRepository.findByEndpoint("https://push.example/subscription")).thenReturn(Optional.empty());

        subscriptionService.cancel(CancelOpenNotificationSubscriptionRequest.builder()
                .endpoint("https://push.example/subscription")
                .build());

        verify(subscriptionRepository).findByEndpoint("https://push.example/subscription");
    }

    private static CreateOpenNotificationSubscriptionRequest request() {
        return CreateOpenNotificationSubscriptionRequest.builder()
                .endpoint("https://push.example/subscription")
                .keys(CreateOpenNotificationSubscriptionRequest.Keys.builder()
                        .p256dh("p256dh-key")
                        .auth("auth-key")
                        .build())
                .build();
    }

    private static OpenNotificationProperties properties() {
        return new OpenNotificationProperties("public-key", "private-key", "mailto:team@yufesta.com", OPEN_AT,
                3, Duration.ofMinutes(1), Duration.ofMinutes(2), 20);
    }

    private static Clock clockAt(String now) {
        return Clock.fixed(Instant.parse(now), KST);
    }
}
