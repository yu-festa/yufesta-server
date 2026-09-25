-- ============================================================
-- 부하 테스트용 합성 회원·신청 생성. 실사용자가 없는 기간에만 쓴다.
--   docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/seed.sql
--   운영은 dbshell(infra/README.md 3-1)에 붙여넣기
-- 끝에 출력되는 min_user_id / user_count 를 k6의 -e USER_ID_FROM, -e USER_COUNT 로 넘긴다.
-- 정리는 load/cleanup.sql. 합성 계정은 provider_user_id가 'load-'로 시작해 실제 회원과 구분된다.
-- ============================================================

SET SESSION cte_max_recursion_depth = 20000;

-- 현재 접수 중인 회차에 신청을 만든다. OPEN 회차가 없으면 아무것도 넣지 않는다
SET @round_id = (SELECT id FROM match_rounds WHERE status = 'OPEN' ORDER BY seq LIMIT 1);
SET @publish_at = (SELECT publish_at FROM match_rounds WHERE id = @round_id);
SET @count = 1000;

-- 1) 합성 회원
INSERT INTO users (provider, provider_user_id, role, last_login_at, created_at, updated_at)
WITH RECURSIVE nums AS (
  SELECT 1 AS i UNION ALL SELECT i + 1 FROM nums WHERE i < 1000
)
SELECT 'KAKAO', CONCAT('load-', i), 'USER', NOW(), NOW(), NOW() FROM nums;

-- 2) 보고 싶은 공연 후보(해당 회차 발표 이후 시작하는 공연만, FR-MT-05)
DROP TEMPORARY TABLE IF EXISTS load_slots;
CREATE TEMPORARY TABLE load_slots (rn INT, slot_id BIGINT UNSIGNED);
INSERT INTO load_slots
SELECT ROW_NUMBER() OVER (ORDER BY s.id) - 1, s.id
FROM timetable_slots s WHERE s.start_at >= @publish_at;
SET @slot_count = (SELECT GREATEST(COUNT(*), 1) FROM load_slots);

-- 3) 신청 (성비 55:45, 나이대 4종 순환, 40%가 공연 선택)
INSERT INTO applications
  (user_id, round_id, instagram_id, nickname, gender, age_band, wanted_slot_id, intro,
   entry_type, terms_version, privacy_version, age_confirmed, agreed_at, created_at, updated_at)
SELECT u.id, @round_id, CONCAT('load.', u.id), CONCAT('부하', u.id),
       IF(u.id % 100 < 55, 'M', 'F'),
       ELT(1 + (u.id % 4), '19-21', '22-24', '25-27', '28+'),
       IF(u.id % 10 < 4, ls.slot_id, NULL),
       NULL, 'NEW', 'v1', 'v1', 1, NOW(), NOW(), NOW()
FROM users u
LEFT JOIN load_slots ls ON ls.rn = u.id % @slot_count
WHERE u.provider_user_id LIKE 'load-%'
  AND @round_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM applications a WHERE a.user_id = u.id AND a.round_id = @round_id);

-- 4) 관심 태그 1~2개 (고정 10개 목록 안에서)
INSERT IGNORE INTO application_tags (application_id, tag)
SELECT a.id, ELT(1 + (a.id % 10),
       'ALCOHOL', 'PERFORMANCE', 'SPORTS', 'GAME', 'CAFE', 'MOVIE', 'MUSIC', 'PHOTO', 'PET', 'ETC')
FROM applications a WHERE a.instagram_id LIKE 'load.%';

INSERT IGNORE INTO application_tags (application_id, tag)
SELECT a.id, ELT(1 + ((a.id + 3) % 10),
       'ALCOHOL', 'PERFORMANCE', 'SPORTS', 'GAME', 'CAFE', 'MOVIE', 'MUSIC', 'PHOTO', 'PET', 'ETC')
FROM applications a WHERE a.instagram_id LIKE 'load.%' AND a.id % 2 = 0;

-- 5) k6에 넘길 값
SELECT MIN(u.id) AS min_user_id, MAX(u.id) AS max_user_id, COUNT(*) AS user_count,
       @round_id AS seeded_round_id,
       (SELECT COUNT(*) FROM applications a WHERE a.instagram_id LIKE 'load.%') AS applications
FROM users u WHERE u.provider_user_id LIKE 'load-%';
