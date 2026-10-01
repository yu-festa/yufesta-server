-- 최근 가입한 회원 10명. 운영자로 올릴 사람의 소셜 계정 식별자를 찾을 때 쓴다.
--   ./load/dbsql.sh load/recent-users.sql
-- 본인이 로그인한 직후에 돌리면 맨 위에 나온다. display_name은 카카오·구글 표시 이름이다.
-- 출력에 provider_user_id가 있으므로 결과를 채팅·이슈에 붙이지 않는다.
SELECT id, provider, provider_user_id, display_name, role, login_at
FROM users
ORDER BY id DESC
LIMIT 10;
