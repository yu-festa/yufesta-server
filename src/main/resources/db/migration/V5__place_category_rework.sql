-- 지도 장소 카테고리를 STAGE/TOILET/DELIVERY_ZONE으로 통일한다.
-- 기존 BOOTH/AMENITY/INFO 장소는 의미를 임의로 공개 분류하지 않도록 비노출 처리한다.
-- 운영자가 실제 배달존인지 확인한 뒤 필요한 장소만 다시 노출한다.

ALTER TABLE `places`
  MODIFY `category` VARCHAR(20) NOT NULL COMMENT '카테고리(STAGE/TOILET/DELIVERY_ZONE)';

UPDATE `places`
SET `category` = 'DELIVERY_ZONE',
    `is_active` = 0
WHERE `category` IN ('BOOTH', 'AMENITY', 'INFO');
