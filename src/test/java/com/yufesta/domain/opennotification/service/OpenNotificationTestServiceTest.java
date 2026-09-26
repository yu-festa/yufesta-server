package com.yufesta.domain.opennotification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationTestResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenNotificationTestServiceTest {

    @Mock
    private WebPushSender webPushSender;

    @Test
    void 즉시_테스트_푸시는_구독을_저장하지_않고_실제_payload를_전송한다() {
        when(webPushSender.send(any(), any())).thenReturn(new WebPushDeliveryResult(201));
        OpenNotificationTestService service = new OpenNotificationTestService(properties(), webPushSender);

        OpenNotificationTestResponse response = service.sendTest(request());

        assertThat(response.accepted()).isTrue();
        assertThat(response.statusCode()).isEqualTo(201);
        verify(webPushSender).send(
                new OpenNotificationDispatch(0L, "https://push.example/subscription", "p256dh-key", "auth-key"),
                OpenNotificationPayload.festivalOpen()
        );
    }

    @Test
    void VAPID가_설정되지_않으면_테스트_푸시를_발송하지_않는다() {
        OpenNotificationTestService service = new OpenNotificationTestService(emptyProperties(), webPushSender);

        assertThatThrownBy(() -> service.sendTest(request()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OPEN_NOTIFICATION_NOT_CONFIGURED);
    }

    @Test
    void Push_서비스_실패는_테스트_발송_오류로_변환한다() {
        when(webPushSender.send(any(), any())).thenThrow(new WebPushDeliveryException(new IllegalStateException()));
        OpenNotificationTestService service = new OpenNotificationTestService(properties(), webPushSender);

        assertThatThrownBy(() -> service.sendTest(request()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OPEN_NOTIFICATION_TEST_DELIVERY_FAILED);
    }

    @Test
    void Push_서비스가_실패_응답을_보내면_테스트_발송_오류로_변환한다() {
        when(webPushSender.send(any(), any())).thenReturn(new WebPushDeliveryResult(410));
        OpenNotificationTestService service = new OpenNotificationTestService(properties(), webPushSender);

        assertThatThrownBy(() -> service.sendTest(request()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OPEN_NOTIFICATION_TEST_DELIVERY_FAILED);
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
        return new OpenNotificationProperties(
                "public-key", "private-key", "mailto:team@yufesta.com",
                OffsetDateTime.parse("2026-10-02T08:00:00+09:00"),
                3, Duration.ofMinutes(1), Duration.ofMinutes(2), 20
        );
    }

    private static OpenNotificationProperties emptyProperties() {
        return new OpenNotificationProperties(
                "", "", "", OffsetDateTime.parse("2026-10-02T08:00:00+09:00"),
                3, Duration.ofMinutes(1), Duration.ofMinutes(2), 20
        );
    }
}
