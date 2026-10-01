-- ============================================================
-- 매칭 알고리즘 최종 점검용 합성 신청. 성비와 태그 분포를 지정한다(부하용 seed.sql은 55:45 고정).
--   ./load/dbsql.sh load/seed-ratio.sql        (OPEN 회차가 있어야 한다. 없으면 qa-reset.sql 먼저)
-- 끝나면 Swagger에서 회차 close → verify.sql(위반 0 확인) → sample.sql(쌍별 점수 눈으로 확인).
-- 정리는 cleanup.sql(합성 회원 삭제, 신청·매칭 CASCADE) 뒤 qa-reset.sql.
-- 합성 계정은 provider_user_id가 'load-'로 시작해 실제 회원과 구분된다.
-- ============================================================
SET SESSION cte_max_recursion_depth = 20000;

SET @men   = 300;
SET @women = 100;
SET @round_id   = (SELECT id FROM match_rounds WHERE status = 'OPEN' ORDER BY seq LIMIT 1);
SET @publish_at = (SELECT publish_at FROM match_rounds WHERE id = @round_id);

-- 1) 합성 회원. i <= @men 이 남, 나머지가 여
INSERT INTO users (provider, provider_user_id, role, last_login_at, created_at, updated_at)
WITH RECURSIVE nums AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM nums WHERE i < @men + @women)
SELECT 'KAKAO', CONCAT('load-', i), 'USER', NOW(), NOW(), NOW() FROM nums;

-- 2) 보고 싶은 공연 후보(발표 이후 시작, FR-MT-05)
DROP TEMPORARY TABLE IF EXISTS load_slots;
CREATE TEMPORARY TABLE load_slots (rn INT, slot_id BIGINT UNSIGNED);
INSERT INTO load_slots
SELECT ROW_NUMBER() OVER (ORDER BY s.id) - 1, s.id FROM timetable_slots s WHERE s.start_at >= @publish_at;
SET @slot_count = (SELECT GREATEST(COUNT(*), 1) FROM load_slots);

-- 3) 신청. 성별은 번호로, 나이대 4종 순환, 절반이 공연 선택
INSERT INTO applications
  (user_id, round_id, instagram_id, nickname, gender, age_band, wanted_slot_id, intro,
   entry_type, terms_version, privacy_version, age_confirmed, agreed_at, created_at, updated_at)
SELECT u.id, @round_id, CONCAT('load.', u.id), CONCAT('점검', u.id),
       IF(CAST(SUBSTRING(u.provider_user_id, 6) AS UNSIGNED) <= @men, 'M', 'F'),
       ELT(1 + (u.id % 4), '19-21', '22-24', '25-27', '28+'),
       IF(u.id % 2 = 0, ls.slot_id, NULL),
       NULL, 'NEW', 'v1', 'v1', 1, NOW(), NOW(), NOW()
FROM users u
LEFT JOIN load_slots ls ON ls.rn = u.id % @slot_count
WHERE u.provider_user_id LIKE 'load-%'
  AND @round_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM applications a WHERE a.user_id = u.id AND a.round_id = @round_id);

-- 4) 태그 0~3개를 고르게. 신청 id % 4 가 개수, 태그는 (id*7 + k) % 10 으로 흩뿌린다
INSERT IGNORE INTO application_tags (application_id, tag)
SELECT a.id, ELT(1 + ((a.id * 7 + k.k) % 10),
       'ALCOHOL', 'PERFORMANCE', 'SPORTS', 'GAME', 'CAFE', 'MOVIE', 'MUSIC', 'PHOTO', 'PET', 'ETC')
FROM applications a
JOIN (SELECT 0 AS k UNION ALL SELECT 1 UNION ALL SELECT 2) k ON k.k < a.id % 4
WHERE a.instagram_id LIKE 'load.%';

-- 5) 확인
SELECT gender, COUNT(*) AS applicants,
       ROUND(AVG((SELECT COUNT(*) FROM application_tags t WHERE t.application_id = a.id)), 2) AS avg_tags,
       SUM(wanted_slot_id IS NOT NULL) AS with_slot
FROM applications a WHERE a.instagram_id LIKE 'load.%' AND a.round_id = @round_id GROUP BY gender;
SELECT tag, COUNT(*) AS n FROM application_tags t JOIN applications a ON a.id = t.application_id
WHERE a.instagram_id LIKE 'load.%' GROUP BY tag ORDER BY tag;
