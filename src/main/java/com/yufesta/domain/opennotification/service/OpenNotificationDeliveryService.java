package com.yufesta.domain.opennotification.service;

import com.yufesta.domain.opennotification.OpenNotificationProperties;
import com.yufesta.domain.opennotification.entity.OpenNotificationSubscription;
import com.yufesta.domain.opennotification.enums.OpenNotificationStatus;
import com.yufesta.domain.opennotification.repository.OpenNotificationSubscriptionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** DB lease로 다중 ECS 태스크의 중복 Web Push 발송을 막고 결과 상태를 전이한다. */
@Service
@Transactional(readOnly = true)
public class OpenNotificationDeliveryService {

    private static final String DELIVERY_FAILED = "DELIVERY_FAILED";
    private static final String RETRY_EXHAUSTED = "RETRY_EXHAUSTED";

    private final OpenNotificationSubscriptionRepository subscriptionRepository;
    private final OpenNotificationProperties properties;
    private final Clock clock;

    public OpenNotificationDeliveryService(
            OpenNotificationSubscriptionRepository subscriptionRepository,
            OpenNotificationProperties properties,
            Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 발송 시각 이후의 구독 한 건에 PESSIMISTIC_WRITE lease를 잡는다.
     * <p>다른 태스크가 먼저 lease를 잡으면 잠금 해제 뒤 조건에서 제외되므로 같은 endpoint를 다시 보내지 않는다.
     */
    @Transactional
    public Optional<OpenNotificationDispatch> claimNext() {
        LocalDateTime now = now();
        if (now.isBefore(openAt())) {
            return Optional.empty();
        }
        return subscriptionRepository.findDispatchCandidatesForUpdate(
                        OpenNotificationStatus.ACTIVE,
                        OpenNotificationStatus.SENDING,
                        now,
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .map(subscription -> claim(subscription, now));
    }

    /** Push 서비스 HTTP 응답에 따라 SENT·EXPIRED·재시도 상태로 전이한다. */
    @Transactional
    public void recordResult(Long subscriptionId, WebPushDeliveryResult result) {
        OpenNotificationSubscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElse(null);
        if (subscription == null || subscription.getStatus() != OpenNotificationStatus.SENDING) {
            return;
        }
        if (result.isAccepted()) {
            subscription.markSent(now());
            return;
        }
        if (result.isExpiredSubscription()) {
            subscription.markExpired("HTTP_" + result.statusCode());
            return;
        }
        scheduleRetryOrExpire(subscription, "HTTP_" + result.statusCode());
    }

    /** payload 암호화·네트워크 예외처럼 HTTP 응답이 없는 일시 실패를 재시도한다. */
    @Transactional
    public void recordTemporaryFailure(Long subscriptionId) {
        OpenNotificationSubscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElse(null);
        if (subscription == null || subscription.getStatus() != OpenNotificationStatus.SENDING) {
            return;
        }
        scheduleRetryOrExpire(subscription, DELIVERY_FAILED);
    }

    private OpenNotificationDispatch claim(OpenNotificationSubscription subscription, LocalDateTime now) {
        subscription.claim(now, now.plus(properties.deliveryLease()));
        return new OpenNotificationDispatch(
                subscription.getId(),
                subscription.getEndpoint(),
                subscription.getP256dhKey(),
                subscription.getAuthKey()
        );
    }

    private void scheduleRetryOrExpire(OpenNotificationSubscription subscription, String errorCode) {
        if (subscription.getAttemptCount() >= properties.maxAttempts()) {
            subscription.markExpired(RETRY_EXHAUSTED);
            return;
        }
        subscription.scheduleRetry(now().plus(retryDelay(subscription.getAttemptCount())), errorCode);
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 10);
        return properties.retryBaseDelay().multipliedBy(multiplier);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime openAt() {
        return properties.openAt().atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }
}
