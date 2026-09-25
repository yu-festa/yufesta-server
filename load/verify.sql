-- ============================================================
-- 인스타팅 배치 정합성 검증 (FR-MT-20~24). 실제 MySQL에서 돌린다.
--   로컬: docker compose exec mysql mysql -uroot -p yufesta < load/verify.sql
--   운영: dbshell(infra/README.md 3-1)에서 아래 내용을 붙여넣기
-- 자동 테스트(MatchRoundBatchInvariantTest)와 같은 불변식을 SQL로 본다. 모든 violations가 0이어야 한다.
-- 배치·발표 직후(리허설·부하 테스트 포함)에 실행한다.
-- ============================================================

SELECT '=== 1. 불변식 위반 (모두 0이어야 함) ===' AS report;

WITH
-- 배치 풀 기준: 취소하지 않았고 매칭 차단이 아닌 신청(§8)
pool AS (
  SELECT a.id, a.round_id, a.gender, a.user_id
  FROM applications a JOIN users u ON u.id = a.user_id
  WHERE a.canceled_at IS NULL AND u.matching_blocked_at IS NULL
),
-- 엔진은 인원이 많은 쪽을 다수(1명), 적은 쪽을 소수(최대 N명)로 본다. 동수면 남성이 다수
minority AS (
  SELECT round_id,
         CASE WHEN SUM(gender = 'M') >= SUM(gender = 'F') THEN 'F' ELSE 'M' END AS minority_gender
  FROM pool GROUP BY round_id
),
max_partners AS (
  SELECT CAST(setting_value AS UNSIGNED) AS n FROM app_settings WHERE setting_key = 'match.max_partners'
),
partner_counts AS (
  SELECT round_id, application_id, COUNT(*) AS partners FROM `matches` GROUP BY round_id, application_id
),
-- 회원 쌍 단위(방향 무관)로 본 매칭
user_pairs AS (
  SELECT m.round_id,
         LEAST(a1.user_id, a2.user_id) AS user_lo, GREATEST(a1.user_id, a2.user_id) AS user_hi
  FROM `matches` m
  JOIN applications a1 ON a1.id = m.application_id
  JOIN applications a2 ON a2.id = m.partner_application_id
)
SELECT '1-1 거울 행 누락(A→B는 있고 B→A가 없음)' AS check_name, COUNT(*) AS violations FROM `matches` m
  WHERE NOT EXISTS (SELECT 1 FROM `matches` r
                    WHERE r.application_id = m.partner_application_id
                      AND r.partner_application_id = m.application_id
                      AND r.round_id = m.round_id AND r.score = m.score)
UNION ALL
SELECT '1-2 자기 자신과 매칭', COUNT(*) FROM `matches` WHERE application_id = partner_application_id
UNION ALL
SELECT '1-3 동성 매칭', COUNT(*) FROM `matches` m
  JOIN applications a1 ON a1.id = m.application_id
  JOIN applications a2 ON a2.id = m.partner_application_id
  WHERE a1.gender = a2.gender
UNION ALL
SELECT '1-4 다른 회차 신청끼리 매칭', COUNT(*) FROM `matches` m
  JOIN applications a1 ON a1.id = m.application_id
  JOIN applications a2 ON a2.id = m.partner_application_id
  WHERE a1.round_id <> m.round_id OR a2.round_id <> m.round_id
UNION ALL
SELECT '1-5 차단 쌍이 매칭됨(양방향)', COUNT(*) FROM user_pairs up
  WHERE EXISTS (SELECT 1 FROM blocks b
                WHERE (b.reporter_user_id = up.user_lo AND b.target_user_id = up.user_hi)
                   OR (b.reporter_user_id = up.user_hi AND b.target_user_id = up.user_lo))
UNION ALL
SELECT '1-6 이전 회차에 매칭된 쌍이 다시 매칭', COUNT(*) FROM (
    SELECT user_lo, user_hi FROM user_pairs GROUP BY user_lo, user_hi HAVING COUNT(DISTINCT round_id) > 1
  ) repeated
UNION ALL
SELECT '1-7 파트너 수 상한 위반(다수 1명·소수 N명)', COUNT(*) FROM partner_counts pc
  JOIN applications a ON a.id = pc.application_id
  JOIN minority mi ON mi.round_id = pc.round_id
  CROSS JOIN max_partners mp
  WHERE pc.partners > CASE WHEN a.gender = mi.minority_gender THEN mp.n ELSE 1 END
UNION ALL
SELECT '1-8 취소·차단 회원이 결과에 포함됨', COUNT(*) FROM `matches` m
  JOIN applications a ON a.id = m.application_id
  JOIN users u ON u.id = a.user_id
  WHERE a.canceled_at IS NOT NULL OR u.matching_blocked_at IS NOT NULL
UNION ALL
SELECT '1-9 회차 발표 전에 시작하는 공연을 선택', COUNT(*) FROM applications a
  JOIN match_rounds r ON r.id = a.round_id
  JOIN timetable_slots s ON s.id = a.wanted_slot_id
  WHERE s.start_at < r.publish_at
UNION ALL
SELECT '1-10 미매칭인데 다음 회차로 이월되지 않음', COUNT(*) FROM applications a
  JOIN match_rounds r ON r.id = a.round_id AND r.published_at IS NOT NULL
  JOIN match_rounds nxt ON nxt.seq = r.seq + 1
  JOIN users u ON u.id = a.user_id AND u.matching_blocked_at IS NULL
  WHERE a.canceled_at IS NULL
    AND NOT EXISTS (SELECT 1 FROM `matches` m WHERE m.application_id = a.id)
    AND NOT EXISTS (SELECT 1 FROM applications c WHERE c.user_id = a.user_id AND c.round_id = nxt.id)
UNION ALL
SELECT '1-11 점수·배정 차수 이상', COUNT(*) FROM `matches`
  WHERE score IS NULL OR score < 0 OR assign_pass NOT IN (1, 2)
UNION ALL
SELECT '1-12 발표 전 회차에 결과가 노출 가능(published_at 없이 matches 존재는 정상, CLOSED 여부 확인용)', COUNT(*)
  FROM `matches` m JOIN match_rounds r ON r.id = m.round_id WHERE r.status IN ('SCHEDULED', 'OPEN');

SELECT '=== 2. 회차별 요약 (기록용) ===' AS report;

-- 별칭은 ASCII로 둔다(mysql CLI로 파일을 파이프하면 한글 별칭이 깨질 수 있다)
SELECT r.seq AS round_seq, r.status,
       COUNT(DISTINCT a.id) AS pool,
       SUM(a.gender = 'M') AS men, SUM(a.gender = 'F') AS women,
       (SELECT COUNT(DISTINCT m.application_id) FROM `matches` m WHERE m.round_id = r.id) AS matched,
       COUNT(DISTINCT a.id) - (SELECT COUNT(DISTINCT m.application_id) FROM `matches` m WHERE m.round_id = r.id) AS unmatched,
       (SELECT COUNT(*) / 2 FROM `matches` m WHERE m.round_id = r.id) AS pairs,
       (SELECT ROUND(AVG(m.score), 2) FROM `matches` m WHERE m.round_id = r.id) AS avg_score,
       (SELECT COUNT(*) FROM applications c WHERE c.round_id = r.id AND c.entry_type = 'CARRIED') AS carried,
       TIMESTAMPDIFF(SECOND, r.close_at, r.executed_at) AS batch_lag_sec
FROM match_rounds r
LEFT JOIN applications a ON a.round_id = r.id AND a.canceled_at IS NULL
LEFT JOIN users u ON u.id = a.user_id AND u.matching_blocked_at IS NULL
GROUP BY r.id, r.seq, r.status, r.close_at, r.executed_at
ORDER BY r.seq;
