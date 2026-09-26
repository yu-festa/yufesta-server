package com.yufesta.domain.opennotification.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.service.OpenNotificationDeliveryService;
import com.yufesta.domain.opennotification.service.OpenNotificationDispatch;
import com.yufesta.domain.opennotification.service.WebPushDeliveryException;
import com.yufesta.domain.opennotification.service.WebPushDeliveryResult;
import com.yufesta.domain.opennotification.service.WebPushSender;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenNotificationSchedulerTest {

    @Mock
    private OpenNotificationDeliveryService deliveryService;

    @Mock
    private WebPushSender webPushSender;

    @Test
    void claim한_구독을_발송하고_성공_결과를_기록한다() {
        OpenNotificationDispatch dispatch = new OpenNotificationDispatch(1L, "https://push.example/1", "p256dh", "auth");
        AtomicInteger claimCount = new AtomicInteger();
        when(deliveryService.claimNext()).thenAnswer(invocation ->
                claimCount.getAndIncrement() == 0 ? Optional.of(dispatch) : Optional.empty());
        when(webPushSender.send(any(), any())).thenReturn(new WebPushDeliveryResult(201));

        scheduler(properties()).tick();

        verify(webPushSender).send(dispatch, com.yufesta.domain.opennotification.service.OpenNotificationPayload.festivalOpen());
        verify(deliveryService).recordResult(1L, new WebPushDeliveryResult(201));
    }

    @Test
    void 발송_예외는_상태를_남긴_채_재시도_처리한다() {
        OpenNotificationDispatch dispatch = new OpenNotificationDispatch(1L, "https://push.example/1", "p256dh", "auth");
        AtomicInteger claimCount = new AtomicInteger();
        when(deliveryService.claimNext()).thenAnswer(invocation ->
                claimCount.getAndIncrement() == 0 ? Optional.of(dispatch) : Optional.empty());
        when(webPushSender.send(any(), any())).thenThrow(new WebPushDeliveryException(new IllegalStateException()));

        assertThatCode(() -> scheduler(properties()).tick()).doesNotThrowAnyException();

        verify(deliveryService).recordTemporaryFailure(1L);
    }

    @Test
    void VAPID_설정이_없으면_claim하지_않는다() {
        scheduler(new OpenNotificationProperties("", "", "", openAt(), 3, Duration.ofMinutes(1), Duration.ofMinutes(2), 20)).tick();

        verify(deliveryService, never()).claimNext();
    }

    private OpenNotificationScheduler scheduler(OpenNotificationProperties properties) {
        return new OpenNotificationScheduler(deliveryService, webPushSender, properties);
    }

    private static OpenNotificationProperties properties() {
        return new OpenNotificationProperties("public", "private", "mailto:team@yufesta.com", openAt(),
                3, Duration.ofMinutes(1), Duration.ofMinutes(2), 20);
    }

    private static OffsetDateTime openAt() {
        return OffsetDateTime.parse("2026-10-02T00:00:00+09:00");
    }
}
