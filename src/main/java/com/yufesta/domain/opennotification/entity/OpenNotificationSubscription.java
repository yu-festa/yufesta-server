package com.yufesta.domain.opennotification.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.opennotification.enums.OpenNotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 없이 생성되는 브라우저 단위 서비스 오픈 Web Push 구독 */
@Getter
@Entity
@Table(
        name = "open_notification_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_open_notification_subscriptions_endpoint", columnNames = "endpoint")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenNotificationSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, length = 255)
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, length = 255)
    private String authKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OpenNotificationStatus status;

    @Column(nullable = false)
    private int attemptCount;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime deliveryLeaseUntil;

    private LocalDateTime deliveredAt;

    private LocalDateTime cancelledAt;

    @Column(length = 100)
    private String lastError;

    @Builder
    private OpenNotificationSubscription(String endpoint, String p256dhKey, String authKey) {
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.status = OpenNotificationStatus.ACTIVE;
        this.attemptCount = 0;
    }

    /** 같은 endpoint의 최신 브라우저 키를 반영하고 다시 발송 대상으로 활성화한다. */
    public void reactivate(String p256dhKey, String authKey) {
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.status = OpenNotificationStatus.ACTIVE;
        this.attemptCount = 0;
        this.nextAttemptAt = null;
        this.deliveryLeaseUntil = null;
        this.deliveredAt = null;
        this.cancelledAt = null;
        this.lastError = null;
    }

    /** 발송 작업을 한 서버만 소유하도록 lease를 획득한다. */
    public void claim(LocalDateTime now, LocalDateTime leaseUntil) {
        this.status = OpenNotificationStatus.SENDING;
        this.attemptCount++;
        this.nextAttemptAt = null;
        this.deliveryLeaseUntil = leaseUntil;
        this.lastError = null;
    }

    /** Push 서비스가 수락한 구독을 재발송하지 않도록 확정한다. */
    public void markSent(LocalDateTime now) {
        this.status = OpenNotificationStatus.SENT;
        this.deliveredAt = now;
        this.deliveryLeaseUntil = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    /** Push 서비스가 더 이상 유효하지 않다고 알려 준 구독을 종료한다. */
    public void markExpired(String errorCode) {
        this.status = OpenNotificationStatus.EXPIRED;
        this.deliveryLeaseUntil = null;
        this.nextAttemptAt = null;
        this.lastError = errorCode;
    }

    /** 일시 실패한 발송을 다음 시각에 재시도하도록 되돌린다. */
    public void scheduleRetry(LocalDateTime nextAttemptAt, String errorCode) {
        this.status = OpenNotificationStatus.ACTIVE;
        this.deliveryLeaseUntil = null;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = errorCode;
    }

    /** 해당 브라우저가 요청한 구독 취소를 멱등적으로 기록한다. */
    public void cancel(LocalDateTime now) {
        if (status == OpenNotificationStatus.SENT || status == OpenNotificationStatus.EXPIRED) {
            return;
        }
        this.status = OpenNotificationStatus.CANCELLED;
        this.cancelledAt = now;
        this.deliveryLeaseUntil = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }
}
