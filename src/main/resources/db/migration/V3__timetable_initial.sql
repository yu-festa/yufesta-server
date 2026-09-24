-- ============================================================
-- V3: 타임테이블 초기 데이터. 총동연 들풀제 타임라인(2026-10-02) 공연 13건과 무대 장소 1건.
-- 무대는 STAGE 장소가 하나도 없을 때만 임시 좌표로 만들고, 있으면 가장 먼저 만든 STAGE 장소에 연결한다(위치는 운영자 API로 수정).
-- 팔찌 배부·입장 안내는 공연이 아니라 공지로 다룬다. 아티스트 공연은 이름·종료 시각 미정이라 임시값이며 운영자 API(admin/timetable)로 고친다.
-- docs/erd.sql 하단과 같은 내용을 유지한다.
-- ============================================================

INSERT INTO `places` (`name`, `category`, `lat`, `lng`, `description`, `sort_order`, `is_active`, `created_at`, `updated_at`)
SELECT '중앙 무대', 'STAGE', 35.8365210, 128.7542100, '들풀제 공연 무대', 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `places` WHERE `category` = 'STAGE');

INSERT INTO `timetable_slots` (`sort_order`, `title`, `slot_type`, `start_at`, `end_at`, `stage_place_id`, `created_at`, `updated_at`) VALUES
  (1,  '개회식',            'EVENT', '2026-10-02 15:00:00', '2026-10-02 15:10:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (2,  '신명마당',          'CLUB',  '2026-10-02 15:30:00', '2026-10-02 15:55:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (3,  '천마응원단',        'CLUB',  '2026-10-02 15:55:00', '2026-10-02 16:15:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (4,  'HIPCOM',            'CLUB',  '2026-10-02 16:15:00', '2026-10-02 16:45:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (5,  'COSMOS',            'CLUB',  '2026-10-02 16:45:00', '2026-10-02 17:15:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (6,  'The WE',            'CLUB',  '2026-10-02 17:15:00', '2026-10-02 17:45:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (7,  'ECHOES',            'CLUB',  '2026-10-02 17:45:00', '2026-10-02 18:15:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (8,  '총장님 연설',       'EVENT', '2026-10-02 18:15:00', '2026-10-02 18:30:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (9,  '예사가락',          'CLUB',  '2026-10-02 18:30:00', '2026-10-02 19:00:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (10, 'BLUE WAVE',         'CLUB',  '2026-10-02 19:00:00', '2026-10-02 19:30:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (11, 'MAX & ZENITH',      'CLUB',  '2026-10-02 19:30:00', '2026-10-02 20:00:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (12, '가요제',            'EVENT', '2026-10-02 20:00:00', '2026-10-02 21:10:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW()),
  (13, '아티스트 공연(미정)', 'GUEST', '2026-10-02 21:30:00', '2026-10-02 23:00:00', (SELECT MIN(`id`) FROM `places` WHERE `category` = 'STAGE'), NOW(), NOW());
