-- 분실물 댓글·답글과 글 단위 익명 닉네임을 추가한다.

ALTER TABLE `content_reports`
  MODIFY `target_type` VARCHAR(20) NOT NULL COMMENT '대상 유형(CHEER/LOST_ITEM/LOST_ITEM_COMMENT)';

CREATE TABLE `lost_item_comment_aliases` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '분실물 댓글 별칭 ID',
  `lost_item_id` BIGINT UNSIGNED NOT NULL COMMENT '분실물 게시글 ID',
  `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '회원 ID',
  `display_name` VARCHAR(20)     NOT NULL COMMENT '글 단위 자동 생성 닉네임',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lost_item_comment_alias_user` (`lost_item_id`, `user_id`),
  UNIQUE KEY `uk_lost_item_comment_alias_name` (`lost_item_id`, `display_name`),
  CONSTRAINT `fk_lost_item_comment_alias_lost_item` FOREIGN KEY (`lost_item_id`) REFERENCES `lost_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lost_item_comment_alias_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='분실물 댓글 글 단위 익명 별칭';

CREATE TABLE `lost_item_comments` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '분실물 댓글 ID',
  `lost_item_id`      BIGINT UNSIGNED NOT NULL COMMENT '분실물 게시글 ID',
  `parent_comment_id` BIGINT UNSIGNED NULL     COMMENT '부모 댓글 ID. NULL이면 최상위 댓글',
  `author_user_id`    BIGINT UNSIGNED NULL     COMMENT '작성자 회원 ID',
  `content`           VARCHAR(200)    NOT NULL COMMENT '댓글 내용',
  `display_name`      VARCHAR(20)     NOT NULL COMMENT '글 단위 자동 생성 닉네임',
  `moderation_status` VARCHAR(10)     NOT NULL DEFAULT 'PASSED' COMMENT '필터 상태(PASSED/SKIPPED)',
  `report_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '신고 누적 수',
  `is_hidden`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '운영자 숨김 여부',
  `is_deleted`        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '작성자 삭제 여부',
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 시각',
  `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_lost_item_comments_lost_item` (`lost_item_id`, `created_at`),
  KEY `idx_lost_item_comments_parent` (`parent_comment_id`, `created_at`),
  CONSTRAINT `fk_lost_item_comments_lost_item` FOREIGN KEY (`lost_item_id`) REFERENCES `lost_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lost_item_comments_parent` FOREIGN KEY (`parent_comment_id`) REFERENCES `lost_item_comments` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_lost_item_comments_user` FOREIGN KEY (`author_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='분실물 댓글·답글';
