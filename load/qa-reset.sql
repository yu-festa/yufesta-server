-- ============================================================
-- ⚠ QA·리허설 뒤 인스타팅을 축제 전 상태로 되돌린다.
--   지우는 것: 모든 신청·매칭·신고
--   푸는 것  : 매칭 차단·작성 금지(QA에서 신고 누적을 시험하면 팀원 계정이 차단된 채로 남는다)
--   되돌리는 것: 회차 상태와 시각
--
--   ./load/dbsql.sh load/qa-reset.sql
--
-- reset-rounds.sql과 다른 점은 두 가지다.
--   1) 회원의 제재 표시를 푼다. reset-rounds.sql은 신고(blocks)만 지우고 users.matching_blocked_at은 그대로 둔다.
--   2) 회차 시각을 상태와 함께 되돌린다. 리허설로 시각을 과거로 옮긴 뒤 상태만 되돌리면
--      스케줄러가 "시각이 지났다"고 보고 1분 안에 마감·발표를 전부 다시 실행한다.
--      발표된 회차는 API로 시각을 고칠 수도 없다. 그래서 한 문장으로 같이 되돌린다.
--
-- 실제 사용자가 신청하기 시작한 뒤에는 절대 실행하지 않는다. 축제 당일에도 실행하지 않는다.
-- 실행 후 1회차는 open_at이 이미 지났으므로 스케줄러가 10초 안에 OPEN으로 연다.
-- ============================================================

-- 축제 시각. 공식 일정이 바뀌면 여기만 고친다
SET @round1_open    = '2026-09-23 00:00:00';
SET @round1_close   = '2026-10-02 15:50:00';
SET @round1_publish = '2026-10-02 16:00:00';
SET @round2_open    = '2026-10-02 16:00:00';
SET @round2_close   = '2026-10-02 19:50:00';
SET @round2_publish = '2026-10-02 20:00:00';

SELECT '=== before ===' AS report;
SELECT (SELECT COUNT(*) FROM applications) AS applications,
       (SELECT COUNT(*) FROM `matches`) AS matches,
       (SELECT COUNT(*) FROM blocks) AS blocks,
       (SELECT COUNT(*) FROM users WHERE matching_blocked_at IS NOT NULL) AS matching_blocked_users,
       (SELECT COUNT(*) FROM users WHERE write_banned_at IS NOT NULL) AS write_banned_users,
       (SELECT COUNT(*) FROM users WHERE provider_user_id LIKE 'load-%') AS load_users_left;

DELETE FROM `matches`;
DELETE FROM blocks;
DELETE FROM applications;

UPDATE users
SET matching_blocked_at = NULL, write_banned_at = NULL
WHERE matching_blocked_at IS NOT NULL OR write_banned_at IS NOT NULL;

UPDATE match_rounds
SET status       = 'SCHEDULED',
    executed_at  = NULL,
    published_at = NULL,
    open_at      = CASE seq WHEN 1 THEN @round1_open    WHEN 2 THEN @round2_open    END,
    close_at     = CASE seq WHEN 1 THEN @round1_close   WHEN 2 THEN @round2_close   END,
    publish_at   = CASE seq WHEN 1 THEN @round1_publish WHEN 2 THEN @round2_publish END
WHERE seq IN (1, 2);

SELECT '=== after ===' AS report;
SELECT r.id, r.seq, r.status, r.open_at, r.close_at, r.publish_at, r.executed_at, r.published_at,
       (SELECT COUNT(*) FROM applications a WHERE a.round_id = r.id) AS applications
FROM match_rounds r ORDER BY r.seq;
SELECT (SELECT COUNT(*) FROM users WHERE matching_blocked_at IS NOT NULL) AS matching_blocked_users,
       (SELECT COUNT(*) FROM users WHERE write_banned_at IS NOT NULL) AS write_banned_users;
