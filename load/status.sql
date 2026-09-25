-- ============================================================
-- 지금 어느 단계까지 준비돼 있는지 한눈에 본다. 세션을 시작하기 전·후에 실행한다.
--   docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/status.sql
--   운영은 dbshell(infra/README.md 3-1)에 붙여넣기
-- ============================================================

SELECT '=== 합성 데이터 (seed.sql) ===' AS report;

SELECT COUNT(*) AS load_users,
       MIN(id) AS min_user_id,          -- k6 -e USER_ID_FROM
       MAX(id) AS max_user_id,
       (SELECT COUNT(*) FROM applications WHERE instagram_id LIKE 'load.%') AS load_applications
FROM users WHERE provider_user_id LIKE 'load-%';

SELECT '=== 회차 상태 ===' AS report;

SELECT r.seq, r.status, r.open_at, r.close_at, r.publish_at, r.executed_at, r.published_at,
       (SELECT COUNT(*) FROM applications a WHERE a.round_id = r.id AND a.canceled_at IS NULL) AS applications,
       (SELECT COUNT(*) / 2 FROM `matches` m WHERE m.round_id = r.id) AS pairs
FROM match_rounds r ORDER BY r.seq;

SELECT '=== DB 커넥션 여유 (풀 크기 결정용) ===' AS report;

-- 태스크 수 × Hikari maximum-pool-size 가 max_connections보다 충분히 작아야 한다.
-- 롤링 배포 중에는 태스크가 2배가 되므로 그 상태(4 × 풀 크기)도 견뎌야 한다
SELECT @@max_connections AS max_connections,
       (SELECT COUNT(*) FROM information_schema.processlist) AS current_connections,
       @@max_connections - (SELECT COUNT(*) FROM information_schema.processlist) AS headroom;

SELECT '=== 세션별 준비 여부 ===' AS report;

SELECT
  IF((SELECT COUNT(*) FROM users WHERE provider_user_id LIKE 'load-%') > 0, 'OK', '먼저 seed.sql') AS `A_쓰기(시드 필요)`,
  IF((SELECT COUNT(*) FROM match_rounds WHERE status = 'OPEN') > 0, 'OK', '회차가 OPEN이 아님') AS `A_쓰기(OPEN 필요)`,
  IF((SELECT COUNT(*) FROM match_rounds WHERE status = 'OPEN') > 0, 'OK', '마감할 OPEN 회차 없음') AS `B_마감`,
  IF((SELECT COUNT(*) FROM match_rounds WHERE published_at IS NOT NULL) > 0, 'OK', '아직 발표 전') AS `C_결과조회(발표 필요)`;
