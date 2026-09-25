-- ============================================================
-- 매칭 결과를 눈으로 확인한다. verify.sql이 "규칙 위반 0"을 보는 자동 검사라면 이 파일은 실제 데이터를 읽기 위한 것이다.
--   로컬: docker compose exec -T mysql mysql --default-character-set=utf8mb4 -uroot -pyufesta yufesta < load/sample.sql
--   운영: dbshell(infra/README.md 3-1)의 mysql> 프롬프트에 붙여넣기
-- 개인정보 주의: instagram_id는 뽑지 않는다(운영 DB에서도 화면에 남기지 않는다).
-- ============================================================

SELECT '=== 1. 참가자 (최근 회차 20명) ===' AS report;

SELECT a.id, a.round_id AS round, a.gender, a.age_band, a.entry_type,
       (SELECT GROUP_CONCAT(t.tag ORDER BY t.tag SEPARATOR ',')
          FROM application_tags t WHERE t.application_id = a.id) AS tags,
       s.title AS wanted_slot,
       (a.canceled_at IS NOT NULL) AS canceled
FROM applications a
LEFT JOIN timetable_slots s ON s.id = a.wanted_slot_id
WHERE a.round_id = (SELECT MAX(id) FROM match_rounds WHERE EXISTS
                    (SELECT 1 FROM applications x WHERE x.round_id = match_rounds.id))
ORDER BY a.gender, a.id
LIMIT 20;

SELECT '=== 2. 매칭 쌍과 점수 근거 (소수 측 방향만) ===' AS report;

-- 점수 = 공통 태그 수 × match.weight.tag + 같은 공연 × match.weight.slot + 나이대 동일/인접 가산
SELECT r.seq AS round_seq, m.assign_pass AS pass, m.score,
       a1.id AS app_a, a1.gender AS gender_a, a1.age_band AS age_a,
       a2.id AS app_b, a2.age_band AS age_b,
       (SELECT COUNT(*) FROM application_tags t1
          JOIN application_tags t2 ON t2.application_id = a2.id AND t2.tag = t1.tag
         WHERE t1.application_id = a1.id) AS common_tags,
       (a1.wanted_slot_id IS NOT NULL AND a1.wanted_slot_id = a2.wanted_slot_id) AS same_slot,
       CASE WHEN a1.age_band IS NULL OR a2.age_band IS NULL THEN 'none'
            WHEN a1.age_band = a2.age_band THEN 'same' ELSE 'diff' END AS age_match
FROM `matches` m
JOIN match_rounds r ON r.id = m.round_id
JOIN applications a1 ON a1.id = m.application_id
JOIN applications a2 ON a2.id = m.partner_application_id
-- 소수 측(파트너를 여러 명 받는 쪽) 방향만 보면 쌍마다 한 줄이 된다
WHERE (SELECT COUNT(*) FROM `matches` x WHERE x.application_id = a1.id)
      >= (SELECT COUNT(*) FROM `matches` y WHERE y.application_id = a2.id)
ORDER BY r.seq, a1.id, m.score DESC
LIMIT 40;

SELECT '=== 3. 파트너 수 분포 ===' AS report;

SELECT r.seq AS round_seq, a.gender, cnt.partners, COUNT(*) AS people
FROM (SELECT application_id, COUNT(*) AS partners FROM `matches` GROUP BY application_id) cnt
JOIN applications a ON a.id = cnt.application_id
JOIN match_rounds r ON r.id = a.round_id
GROUP BY r.seq, a.gender, cnt.partners
ORDER BY r.seq, a.gender, cnt.partners;

SELECT '=== 4. 미매칭자 (이월 대상) ===' AS report;

SELECT r.seq AS round_seq, a.id, a.gender, a.age_band, a.entry_type,
       (SELECT COUNT(*) FROM applications c WHERE c.user_id = a.user_id AND c.round_id > a.round_id) AS carried_copies
FROM applications a
JOIN match_rounds r ON r.id = a.round_id
JOIN users u ON u.id = a.user_id
WHERE a.canceled_at IS NULL AND u.matching_blocked_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM `matches` m WHERE m.application_id = a.id)
  AND r.executed_at IS NOT NULL
ORDER BY r.seq, a.id
LIMIT 30;
