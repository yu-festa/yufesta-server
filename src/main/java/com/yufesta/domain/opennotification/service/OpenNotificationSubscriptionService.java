package com.yufesta.domain.opennotification.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.dto.request.CancelOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationSubscriptionResponse;
import com.yufesta.domain.opennotification.dto.response.VapidPublicKeyResponse;
import com.yufesta.domain.opennotification.repository.OpenNotificationSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 오픈 알림 구독의 공개키 조회·비로그인 등록·취소를 처리한다. */
@Service
@Transactional(readOnly = true)
public class OpenNotificationSubscriptionService {

    private final OpenNotificationSubscriptionRepository subscriptionRepository;
    private final OpenNotificationProperties properties;
    private final Clock clock;

    public OpenNotificationSubscriptionService(
            OpenNotificationSubscriptionRepository subscriptionRepository,
            OpenNotificationProperties properties,
            Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** 브라우저 구독 생성에 필요한 VAPID 공개키만 반환한다(#110). */
    public VapidPublicKeyResponse getVapidPublicKey() {
        requireVapidConfigured();
        return VapidPublicKeyResponse.builder().publicKey(properties.vapidPublicKey()).build();
    }

    /**
     * 로그인 없이 브라우저 Web Push endpoint를 중복 없이 등록한다(#110).
     * @throws CustomException OPEN_NOTIFICATION_CLOSED, OPEN_NOTIFICATION_NOT_CONFIGURED
     */
    @Transactional
    public OpenNotificationSubscriptionResponse subscribe(CreateOpenNotificationSubscriptionRequest request) {
        requireVapidConfigured();
        rejectIfOpenTimePassed();

        String endpoint = request.endpoint().trim();
        boolean alreadySubscribed = subscriptionRepository.existsByEndpoint(endpoint);
        int affectedRows = subscriptionRepository.upsertActiveSubscription(
                endpoint,
                request.keys().p256dh().trim(),
                request.keys().auth().trim()
        );
        return response(alreadySubscribed || affectedRows != 1);
    }

    /** 해당 endpoint 구독을 멱등적으로 취소한다(#110). */
    @Transactional
    public void cancel(CancelOpenNotificationSubscriptionRequest request) {
        subscriptionRepository.findByEndpoint(request.endpoint().trim())
                .ifPresent(subscription -> subscription.cancel(now()));
    }

    private OpenNotificationSubscriptionResponse response(boolean duplicate) {
        return OpenNotificationSubscriptionResponse.builder()
                .subscribed(true)
                .duplicate(duplicate)
                .scheduledFor(properties.openAt())
                .build();
    }

    private void rejectIfOpenTimePassed() {
        if (!now().isBefore(openAt())) {
            throw new CustomException(ErrorCode.OPEN_NOTIFICATION_CLOSED);
        }
    }

    private void requireVapidConfigured() {
        if (!properties.isVapidConfigured()) {
            throw new CustomException(ErrorCode.OPEN_NOTIFICATION_NOT_CONFIGURED);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime openAt() {
        return properties.openAt().atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }
}
