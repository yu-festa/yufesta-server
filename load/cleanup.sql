-- ============================================================
-- 부하 테스트 정리. 합성 회원을 지우면 신청·태그·매칭·신고가 FK CASCADE로 함께 지워진다.
--   docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/cleanup.sql
-- 회차 상태(마감·발표) 되돌리기는 아래 주석 블록을 따로 실행한다. 실제 신청까지 지우므로 내용을 보고 판단할 것.
-- ============================================================

DELETE FROM users WHERE provider_user_id LIKE 'load-%';

SELECT (SELECT COUNT(*) FROM users WHERE provider_user_id LIKE 'load-%') AS remaining_load_users,
       (SELECT COUNT(*) FROM applications WHERE instagram_id LIKE 'load.%') AS remaining_load_applications,
       (SELECT COUNT(*) FROM applications) AS total_applications,
       (SELECT COUNT(*) FROM `matches`) AS total_matches;

-- 회차를 처음 상태로 되돌린다(부하 테스트로 close·publish를 했다면 필요).
-- 남아 있는 실제 신청까지 지우므로 축제 전에만, 내용을 확인하고 실행한다.
-- DELETE FROM `matches`;
-- DELETE FROM blocks;
-- DELETE FROM applications;
-- UPDATE match_rounds SET status = 'SCHEDULED', executed_at = NULL, published_at = NULL;
