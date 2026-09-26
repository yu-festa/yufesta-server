package com.yufesta.domain.opennotification.repository;

import com.yufesta.domain.opennotification.entity.OpenNotificationSubscription;
import com.yufesta.domain.opennotification.enums.OpenNotificationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 브라우저 Web Push 구독 저장과 발송 lease 조회를 담당 */
public interface OpenNotificationSubscriptionRepository extends JpaRepository<OpenNotificationSubscription, Long> {

    Optional<OpenNotificationSubscription> findByEndpoint(String endpoint);

    boolean existsByEndpoint(String endpoint);

    /** endpoint 유니크 제약을 이용해 동시 재신청도 한 행의 ACTIVE 구독으로 수렴시킨다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into open_notification_subscriptions (
                endpoint, p256dh_key, auth_key, status, attempt_count,
                next_attempt_at, delivery_lease_until, delivered_at, cancelled_at, last_error, created_at, updated_at
            ) values (
                :endpoint, :p256dhKey, :authKey, 'ACTIVE', 0,
                null, null, null, null, null, now(), now()
            ) as new_subscription on duplicate key update
                p256dh_key = new_subscription.p256dh_key,
                auth_key = new_subscription.auth_key,
                status = 'ACTIVE',
                attempt_count = 0,
                next_attempt_at = null,
                delivery_lease_until = null,
                delivered_at = null,
                cancelled_at = null,
                last_error = null,
                updated_at = now()
            """, nativeQuery = true)
    int upsertActiveSubscription(
            @Param("endpoint") String endpoint,
            @Param("p256dhKey") String p256dhKey,
            @Param("authKey") String authKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OpenNotificationSubscription s where s.id = :id")
    Optional<OpenNotificationSubscription> findByIdForUpdate(@Param("id") Long id);

    /** ACTIVE 재시도 대상과 비정상 종료된 SENDING lease를 한 건씩 잠가 가져온다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from OpenNotificationSubscription s
            where (s.status = :active and (s.nextAttemptAt is null or s.nextAttemptAt <= :now))
               or (s.status = :sending and s.deliveryLeaseUntil <= :now)
            order by s.nextAttemptAt asc, s.id asc
            """)
    List<OpenNotificationSubscription> findDispatchCandidatesForUpdate(
            @Param("active") OpenNotificationStatus active,
            @Param("sending") OpenNotificationStatus sending,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
