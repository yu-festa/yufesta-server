package com.yufesta.domain.opennotification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.entity.OpenNotificationSubscription;
import com.yufesta.domain.opennotification.enums.OpenNotificationStatus;
import com.yufesta.domain.opennotification.repository.OpenNotificationSubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpenNotificationDeliveryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 8, 0);

    @Mock
    private OpenNotificationSubscriptionRepository subscriptionRepository;

    private OpenNotificationDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new OpenNotificationDeliveryService(subscriptionRepository, properties(), clockAt(NOW));
    }

    @Test
    void 오픈_시각_이후_ACTIVE_구독은_lease를_얻고_SENDING으로_바뀐다() {
        OpenNotificationSubscription subscription = subscription(1L);
        when(subscriptionRepository.findDispatchCandidatesForUpdate(
                eq(OpenNotificationStatus.ACTIVE), eq(OpenNotificationStatus.SENDING), eq(NOW), any()))
                .thenReturn(List.of(subscription));

        OpenNotificationDispatch dispatch = deliveryService.claimNext().orElseThrow();

        assertThat(dispatch.id()).isEqualTo(1L);
        assertThat(subscription.getStatus()).isEqualTo(OpenNotificationStatus.SENDING);
        assertThat(subscription.getAttemptCount()).isEqualTo(1);
        assertThat(subscription.getDeliveryLeaseUntil()).isEqualTo(NOW.plusMinutes(2));
    }

    @Test
    void Push_서비스가_성공을_반환하면_SENT로_확정한다() {
        OpenNotificationSubscription subscription = claimedSubscription(1L);
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));

        deliveryService.recordResult(1L, new WebPushDeliveryResult(201));

        assertThat(subscription.getStatus()).isEqualTo(OpenNotificationStatus.SENT);
        assertThat(subscription.getDeliveredAt()).isEqualTo(NOW);
        assertThat(subscription.getDeliveryLeaseUntil()).isNull();
    }

    @Test
    void Push_서비스가_410을_반환하면_EXPIRED로_정리한다() {
        OpenNotificationSubscription subscription = claimedSubscription(1L);
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));

        deliveryService.recordResult(1L, new WebPushDeliveryResult(410));

        assertThat(subscription.getStatus()).isEqualTo(OpenNotificationStatus.EXPIRED);
        assertThat(subscription.getLastError()).isEqualTo("HTTP_410");
    }

    @Test
    void 일시_실패는_exponential_backoff로_ACTIVE_재시도_대기열에_넣는다() {
        OpenNotificationSubscription subscription = claimedSubscription(1L);
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));

        deliveryService.recordResult(1L, new WebPushDeliveryResult(503));

        assertThat(subscription.getStatus()).isEqualTo(OpenNotificationStatus.ACTIVE);
        assertThat(subscription.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(subscription.getLastError()).isEqualTo("HTTP_503");
    }

    @Test
    void 최대_시도_횟수에_도달한_일시_실패는_더_보내지_않는다() {
        OpenNotificationSubscription subscription = claimedSubscription(1L);
        subscription.claim(NOW, NOW.plusMinutes(2));
        subscription.claim(NOW, NOW.plusMinutes(2));
        when(subscriptionRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(subscription));

        deliveryService.recordTemporaryFailure(1L);

        assertThat(subscription.getStatus()).isEqualTo(OpenNotificationStatus.EXPIRED);
        assertThat(subscription.getLastError()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void 오픈_시각_전에는_발송_대상을_claim하지_않는다() {
        OpenNotificationDeliveryService beforeOpenService = new OpenNotificationDeliveryService(
                subscriptionRepository, properties(), clockAt(NOW.minusSeconds(1)));

        assertThat(beforeOpenService.claimNext()).isEmpty();
    }

    private static OpenNotificationSubscription claimedSubscription(Long id) {
        OpenNotificationSubscription subscription = subscription(id);
        subscription.claim(NOW, NOW.plusMinutes(2));
        return subscription;
    }

    private static OpenNotificationSubscription subscription(Long id) {
        OpenNotificationSubscription subscription = OpenNotificationSubscription.builder()
                .endpoint("https://push.example/" + id)
                .p256dhKey("p256dh-key")
                .authKey("auth-key")
                .build();
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    private static OpenNotificationProperties properties() {
        return new OpenNotificationProperties(
                "public-key", "private-key", "mailto:team@yufesta.com",
                OffsetDateTime.parse("2026-10-02T08:00:00+09:00"),
                3, Duration.ofMinutes(1), Duration.ofMinutes(2), 20
        );
    }

    private static Clock clockAt(LocalDateTime now) {
        return Clock.fixed(now.atZone(KST).toInstant(), KST);
    }
}
