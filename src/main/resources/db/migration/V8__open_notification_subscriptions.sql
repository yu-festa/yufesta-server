-- 로그인 없이 브라우저가 등록하는 서비스 오픈 Web Push 구독. endpoint 유니크로 중복 발송을 막는다.
CREATE TABLE `open_notification_subscriptions` (
  `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '서비스 오픈 알림 구독 ID',
  `endpoint`             VARCHAR(500)    NOT NULL COMMENT '브라우저 PushSubscription endpoint',
  `p256dh_key`           VARCHAR(255)    NOT NULL COMMENT 'Web Push P-256 ECDH 공개키',
  `auth_key`             VARCHAR(255)    NOT NULL COMMENT 'Web Push 인증 비밀값',
  `status`               VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE' COMMENT '상태(ACTIVE/SENDING/SENT/CANCELLED/EXPIRED)',
  `attempt_count`        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '발송 시도 횟수',
  `next_attempt_at`      DATETIME        NULL COMMENT '일시 실패 후 다음 재시도 시각',
  `delivery_lease_until` DATETIME        NULL COMMENT '다중 태스크 중 발송 작업 소유 lease 만료 시각',
  `delivered_at`         DATETIME        NULL COMMENT 'Push 서비스 수락 시각',
  `cancelled_at`         DATETIME        NULL COMMENT '브라우저 구독 취소 시각',
  `last_error`           VARCHAR(100)    NULL COMMENT '마지막 발송 결과 코드(민감한 endpoint·키는 저장하지 않음)',
  `created_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '구독 생성 시각',
  `updated_at`           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_open_notification_subscriptions_endpoint` (`endpoint`),
  KEY `idx_open_notification_subscriptions_retry` (`status`, `next_attempt_at`),
  KEY `idx_open_notification_subscriptions_lease` (`status`, `delivery_lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='서비스 오픈 Web Push 구독';
