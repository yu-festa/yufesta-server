-- ============================================================
-- ⚠ 회차를 처음 상태로 되돌린다. 모든 신청·매칭·신고가 지워진다.
-- 부하 테스트나 리허설로 close·publish를 한 뒤 운영을 정상 상태로 복구할 때만 쓴다. 축제 당일에는 절대 실행하지 않는다.
--   ./load/dbsql.sh load/reset-rounds.sql
-- 먼저 load/cleanup.sql로 합성 회원을 지우고, 남은 실제 신청을 확인한 뒤 실행할 것.
-- 실행 후 1회차 open_at이 이미 지났으면 스케줄러가 10초 안에 다시 OPEN으로 연다.
-- ============================================================

SELECT '=== 지우기 전 상태 ===' AS report;
SELECT (SELECT COUNT(*) FROM applications) AS applications,
       (SELECT COUNT(*) FROM `matches`) AS matches,
       (SELECT COUNT(*) FROM blocks) AS blocks,
       (SELECT COUNT(*) FROM users WHERE provider_user_id LIKE 'load-%') AS load_users_left;

DELETE FROM `matches`;
DELETE FROM blocks;
DELETE FROM applications;
UPDATE match_rounds SET status = 'SCHEDULED', executed_at = NULL, published_at = NULL;

SELECT '=== 되돌린 뒤 ===' AS report;
SELECT r.seq, r.status, r.open_at, r.close_at, r.publish_at, r.executed_at, r.published_at,
       (SELECT COUNT(*) FROM applications a WHERE a.round_id = r.id) AS applications
FROM match_rounds r ORDER BY r.seq;
