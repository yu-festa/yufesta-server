-- 분실물 게시글당 본문용·썸네일 이미지 한 쌍만 저장한다.
CREATE TABLE `lost_item_images` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '분실물 이미지 ID',
  `lost_item_id`  BIGINT UNSIGNED NOT NULL COMMENT '분실물 게시글 ID',
  `image_url`     VARCHAR(500)    NOT NULL COMMENT '긴 변 1600px 본문용 JPEG URL',
  `thumbnail_url` VARCHAR(500)    NOT NULL COMMENT '긴 변 400px 썸네일 JPEG URL',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lost_item_images_lost_item` (`lost_item_id`),
  CONSTRAINT `fk_lost_item_images_lost_item` FOREIGN KEY (`lost_item_id`) REFERENCES `lost_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='분실물 게시글 이미지 한 장';
