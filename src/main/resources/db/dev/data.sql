-- dev 전용 초기 데이터. application-dev.yml의 spring.sql.init이 기동 시 실행한다.
-- 출처: docs/erd.sql 초기값(ERD v1.2). INSERT IGNORE라 재기동해도 있는 행은 건너뛴다.
-- created_at·updated_at: Hibernate가 만든 테이블에는 DB 기본값이 없어 시드가 직접 NOW()를 넣는다.
-- admin.allowlist는 비워 둔다. STAFF로 로그인하려면 아래 SQL로 본인 계정을 넣는다.
--   UPDATE app_settings SET setting_value = 'KAKAO:<providerUserId>' WHERE setting_key = 'admin.allowlist';

INSERT IGNORE INTO `app_settings` (`setting_key`, `setting_value`, `description`, `created_at`, `updated_at`) VALUES
  ('match.weight.tag',              '1',   '공통 태그 1개당 점수', NOW(), NOW()),
  ('match.weight.slot',             '2',   '같은 공연 선택 점수', NOW(), NOW()),
  ('match.weight.age_same',         '1',   '나이대 동일 점수', NOW(), NOW()),
  ('match.weight.age_adjacent',     '0.5', '나이대 인접 점수', NOW(), NOW()),
  ('match.max_partners',            '3',   '소수 측 1인당 최대 배정 수(N)', NOW(), NOW()),
  ('report.block_threshold',        '2',   '매칭 신고 누적 제재 기준', NOW(), NOW()),
  ('report.hide_threshold',         '2',   '콘텐츠 신고 누적 자동 숨김 기준', NOW(), NOW()),
  ('nickname.adjectives',           '수줍은,신난,느긋한,씩씩한,조용한', '자동 닉네임 형용사 목록', NOW(), NOW()),
  ('nickname.animals',              '펭귄,수달,고양이,판다,돌고래',   '자동 닉네임 동물 목록', NOW(), NOW()),
  ('filter.banned_words',           '',    '금칙어 목록(쉼표 구분)', NOW(), NOW()),
  ('filter.llm.enabled',            '1',   'LLM 모더레이션 API 사용 여부', NOW(), NOW()),
  ('filter.llm.threshold',          '0.5', '모더레이션 카테고리 차단 임계값(0~1)', NOW(), NOW()),
  ('ratelimit.cheer.anon_per_minute', '1', '응원 메시지 익명 키당 분당 작성 수', NOW(), NOW()),
  ('ratelimit.cheer.ip_per_minute', '10',  '응원 메시지 IP당 분당 작성 수', NOW(), NOW()),
  ('ratelimit.lostitem.per_minute', '1',   '분실물 회원당 분당 작성 수', NOW(), NOW()),
  ('admin.allowlist',               '', '운영자로 승격할 소셜 계정(PROVIDER:id). 최초 로그인 시 role 부여', NOW(), NOW());
-- 초기 회차(SRS FR-MT-01 예시). 실제 시각은 운영자 API로 수정한다. close_at = publish_at - 10분
INSERT IGNORE INTO `match_rounds` (`seq`, `open_at`, `close_at`, `publish_at`, `status`, `created_at`, `updated_at`) VALUES
  (1, '2026-09-25 00:00:00', '2026-10-02 15:50:00', '2026-10-02 16:00:00', 'SCHEDULED', NOW(), NOW()),
  (2, '2026-10-02 16:00:00', '2026-10-02 19:50:00', '2026-10-02 20:00:00', 'SCHEDULED', NOW(), NOW());