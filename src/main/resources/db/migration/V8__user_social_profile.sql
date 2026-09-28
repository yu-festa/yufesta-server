-- 소셜 로그인 제공자의 표시 이름과 프로필 사진을 회원 본인 화면에 제공한다.
ALTER TABLE `users`
  MODIFY COLUMN `display_name` VARCHAR(100) NULL COMMENT '소셜 로그인 표시 이름',
  ADD COLUMN `profile_image_url` VARCHAR(2048) NULL COMMENT '소셜 로그인 프로필 이미지 URL' AFTER `display_name`;
