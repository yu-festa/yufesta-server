-- 회원 한 명을 운영자(STAFF)로 올린다.
--   1) 대상이 yufesta.com에 한 번 로그인한다
--   2) recent-users.sql로 provider와 provider_user_id를 찾는다
--   3) 아래 두 값을 고치고 실행한다:  ./load/dbsql.sh load/grant-staff.sql
--
-- 허용 목록(admin.allowlist)은 회원이 처음 만들어질 때만 확인되므로(UserLoginService), 이미 가입한 사람은
-- 목록에 넣는 것만으로는 승격되지 않는다. 그래서 목록 추가와 role 변경을 함께 한다.
-- 역할은 인증 필터가 최대 60초 기억하고(UserRoleCache) 운영자 경로는 항상 DB를 읽으므로 다시 로그인할 필요가 없다.
-- OWNER(회차 발표·설정 변경)는 주지 않는다. STAFF는 콘텐츠·신고·타임테이블 운영까지다.
-- CONVERT·COLLATE는 접속 클라이언트의 문자셋·콜레이션이 테이블(utf8mb4_unicode_ci)과 달라도 비교가 되게 한다. 없으면 'Illegal mix of collations'
SET @provider         = CONVERT('KAKAO' USING utf8mb4) COLLATE utf8mb4_unicode_ci;      -- KAKAO 또는 GOOGLE
SET @provider_user_id = CONVERT('바꿀_값' USING utf8mb4) COLLATE utf8mb4_unicode_ci;     -- recent-users.sql 결과의 provider_user_id
SET @account = CONCAT(@provider, ':', @provider_user_id) COLLATE utf8mb4_unicode_ci;

UPDATE app_settings
SET setting_value = TRIM(BOTH ',' FROM CONCAT(setting_value, ',', @account)), updated_at = NOW()
WHERE setting_key = 'admin.allowlist' AND NOT FIND_IN_SET(@account, setting_value);

UPDATE users
SET role = 'STAFF', updated_at = NOW()
WHERE provider = @provider AND provider_user_id = @provider_user_id AND role = 'USER';

-- 확인: 대상의 role이 STAFF이고 허용 목록에 계정이 들어 있어야 한다
SELECT id, provider, display_name, role FROM users WHERE provider = @provider AND provider_user_id = @provider_user_id;
SELECT setting_value AS admin_allowlist FROM app_settings WHERE setting_key = 'admin.allowlist';
