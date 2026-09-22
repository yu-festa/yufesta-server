-- ============================================================
-- V1: 초기 스키마. docs/erd.sql(ERD v1.2, SRS v1.6)과 같은 내용을 유지한다.
-- 적용된 파일은 수정하지 않는다. 변경은 V{n}__설명.sql로 추가하고 docs/erd.sql·erd.md도 같은 커밋에서 고친다.
-- ============================================================

-- ------------------------------------------------------------
-- 1. 계정
-- ------------------------------------------------------------
CREATE TABLE `users` (
  `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
  `provider`            VARCHAR(10)     NOT NULL COMMENT '로그인 제공자(KAKAO/GOOGLE)',
  `provider_user_id`    VARCHAR(191)    NOT NULL COMMENT '제공자 계정 식별자',
  `role`                VARCHAR(10)     NOT NULL DEFAULT 'USER' COMMENT '역할(USER/STAFF/OWNER)',
  `display_name`        VARCHAR(30)     NULL     COMMENT '운영자 표시 이름(운영자만)',
  `matching_blocked_at` DATETIME        NULL     COMMENT '매칭 제외 시각',
  `write_banned_at`     DATETIME        NULL     COMMENT '작성 금지 시각',
  `last_login_at`       DATETIME        NULL     COMMENT '최근 로그인 시각',
  `created_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입 시각',
  `updated_at`          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_provider` (`provider`, `provider_user_id`),
  KEY `idx_users_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원(운영자 포함)';

-- ------------------------------------------------------------
-- 2. 축제 정적 데이터 (장소 · 동아리 · 타임테이블)
-- ------------------------------------------------------------
CREATE TABLE `places` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '장소 ID',
  `name`        VARCHAR(50)     NOT NULL COMMENT '장소명',
  `category`    VARCHAR(10)     NOT NULL COMMENT '카테고리(STAGE/BOOTH/TOILET/AMENITY/INFO)',
  `lat`         DECIMAL(10,7)   NOT NULL COMMENT '위도',
  `lng`         DECIMAL(10,7)   NOT NULL COMMENT '경도',
  `description` VARCHAR(200)    NULL     COMMENT '설명',
  `building`    VARCHAR(50)     NULL     COMMENT '건물명',
  `floor`       VARCHAR(20)     NULL     COMMENT '층·세부 위치',
  `sort_order`  INT             NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `is_active`   TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '노출 여부',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_places_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지도 장소(핀)';

CREATE TABLE `place_events` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '장소 이벤트 ID',
  `place_id`   BIGINT UNSIGNED NOT NULL COMMENT '장소 ID',
  `name`       VARCHAR(50)     NOT NULL COMMENT '이벤트명',
  `time_text`  VARCHAR(30)     NOT NULL COMMENT '진행 시간 표기',
  `sort_order` INT             NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_place_events_place` (`place_id`),
  CONSTRAINT `fk_place_events_place` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='장소 진행 이벤트';

CREATE TABLE `clubs` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '동아리 ID',
  `name`           VARCHAR(50)     NOT NULL COMMENT '동아리명',
  `intro`          VARCHAR(200)    NOT NULL COMMENT '소개',
  `genre`          VARCHAR(30)     NULL     COMMENT '장르',
  `signature_song` VARCHAR(50)     NULL     COMMENT '대표곡',
  `instagram_url`  VARCHAR(200)    NULL     COMMENT '인스타그램 링크',
  `photo_url`      VARCHAR(500)    NULL     COMMENT '대표 사진 URL',
  `sort_order`     INT             NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `created_by`     BIGINT UNSIGNED NULL     COMMENT '등록 운영자 ID',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_clubs_admin` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라인업 동아리';

CREATE TABLE `timetable_slots` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '공연 슬롯 ID',
  `sort_order`         INT             NOT NULL COMMENT '공연 순서',
  `title`              VARCHAR(50)     NOT NULL COMMENT '공연명(출연자명)',
  `slot_type`          VARCHAR(10)     NOT NULL COMMENT '구분(CLUB/GUEST)',
  `start_at`           DATETIME        NOT NULL COMMENT '시작 시각',
  `end_at`             DATETIME        NOT NULL COMMENT '종료 시각',
  `stage_place_id`     BIGINT UNSIGNED NOT NULL COMMENT '무대 장소 ID',
  `club_id`            BIGINT UNSIGNED NULL     COMMENT '동아리 ID',
  `changed_from_start` DATETIME        NULL     COMMENT '변경 전 시작 시각',
  `delay_minutes`      INT             NULL     COMMENT '지연 분',
  `is_live_override`   TINYINT(1)      NULL     COMMENT '운영자 지정 진행 중',
  `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_slots_start` (`start_at`),
  CONSTRAINT `fk_slots_stage` FOREIGN KEY (`stage_place_id`) REFERENCES `places` (`id`),
  CONSTRAINT `fk_slots_club`  FOREIGN KEY (`club_id`) REFERENCES `clubs` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공연 타임테이블';

-- ------------------------------------------------------------
-- 3. 인스타팅 (회차 · 신청 · 태그 · 매칭 · 신고)
-- ------------------------------------------------------------
CREATE TABLE `match_rounds` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '회차 ID',
  `seq`          TINYINT UNSIGNED NOT NULL COMMENT '회차 번호',
  `open_at`      DATETIME        NOT NULL COMMENT '접수 시작 시각',
  `close_at`     DATETIME        NOT NULL COMMENT '접수 마감 시각',
  `publish_at`   DATETIME        NOT NULL COMMENT '발표 시각',
  `status`       VARCHAR(12)     NOT NULL DEFAULT 'SCHEDULED' COMMENT '상태(SCHEDULED/OPEN/CLOSED/PUBLISHED)',
  `executed_at`  DATETIME        NULL     COMMENT '배치 실행 시각',
  `published_at` DATETIME        NULL     COMMENT '발표 확정 시각',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rounds_seq` (`seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='매칭 회차';

CREATE TABLE `applications` (
  `id`                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '신청 ID',
  `user_id`               BIGINT UNSIGNED NOT NULL COMMENT '회원 ID',
  `round_id`              BIGINT UNSIGNED NOT NULL COMMENT '회차 ID',
  `instagram_id`          VARCHAR(30)     NOT NULL COMMENT '인스타 ID(정규화)',
  `nickname`              VARCHAR(16)     NOT NULL COMMENT '닉네임',
  `gender`                VARCHAR(1)      NOT NULL COMMENT '성별(M/F)',
  `age_band`              VARCHAR(5)      NULL     COMMENT '나이대(19-21/22-24/25-27/28+)',
  `wanted_slot_id`        BIGINT UNSIGNED NULL     COMMENT '보고 싶은 공연 슬롯 ID(회차 발표 이후 시작 공연만)',
  `intro`                 VARCHAR(40)     NULL     COMMENT '한 줄 소개',
  `entry_type`            VARCHAR(10)     NOT NULL DEFAULT 'NEW' COMMENT '신청 유형(NEW/CARRIED/REJOIN)',
  `source_application_id` BIGINT UNSIGNED NULL     COMMENT '원본 신청 ID(2회차 자동 생성·재참여 시)',
  `terms_version`         VARCHAR(20)     NOT NULL COMMENT '동의한 이용약관 버전',
  `privacy_version`       VARCHAR(20)     NOT NULL COMMENT '동의한 개인정보 방침 버전',
  `age_confirmed`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '19세 이상 확인',
  `agreed_at`             DATETIME        NOT NULL COMMENT '약관 동의 시각',
  `canceled_at`           DATETIME        NULL     COMMENT '취소 시각',
  `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신청 시각',
  `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_round` (`user_id`, `round_id`),
  UNIQUE KEY `uk_app_round_insta` (`round_id`, `instagram_id`),
  KEY `idx_app_round_gender` (`round_id`, `gender`),
  KEY `idx_app_source` (`source_application_id`),
  CONSTRAINT `fk_app_user`   FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_app_round`  FOREIGN KEY (`round_id`) REFERENCES `match_rounds` (`id`),
  CONSTRAINT `fk_app_slot`   FOREIGN KEY (`wanted_slot_id`) REFERENCES `timetable_slots` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_app_source` FOREIGN KEY (`source_application_id`) REFERENCES `applications` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='인스타팅 신청';

CREATE TABLE `application_tags` (
  `application_id` BIGINT UNSIGNED NOT NULL COMMENT '신청 ID',
  `tag`            VARCHAR(20)     NOT NULL COMMENT '관심 태그',
  PRIMARY KEY (`application_id`, `tag`),
  KEY `idx_tags_tag` (`tag`),
  CONSTRAINT `fk_tags_app` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='신청 관심 태그';

CREATE TABLE `matches` (
  `id`                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '매칭 ID',
  `round_id`               BIGINT UNSIGNED NOT NULL COMMENT '회차 ID',
  `application_id`         BIGINT UNSIGNED NOT NULL COMMENT '본인 신청 ID',
  `partner_application_id` BIGINT UNSIGNED NOT NULL COMMENT '상대 신청 ID',
  `score`                  DECIMAL(5,2)    NOT NULL COMMENT '매칭 점수',
  `assign_pass`            TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '배정 단계(1:1차, 2:2차)',
  `created_at`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_pair` (`round_id`, `application_id`, `partner_application_id`),
  KEY `idx_match_app` (`application_id`),
  CONSTRAINT `fk_match_round`   FOREIGN KEY (`round_id`) REFERENCES `match_rounds` (`id`),
  CONSTRAINT `fk_match_app`     FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_match_partner` FOREIGN KEY (`partner_application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='매칭 결과';

CREATE TABLE `blocks` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '매칭 신고 ID',
  `reporter_user_id` BIGINT UNSIGNED NOT NULL COMMENT '신고자 회원 ID',
  `target_user_id`   BIGINT UNSIGNED NOT NULL COMMENT '대상 회원 ID',
  `round_id`         BIGINT UNSIGNED NULL     COMMENT '신고 발생 회차 ID',
  `reason`           VARCHAR(20)     NOT NULL COMMENT '사유(PROFILE/FAKE/OTHER)',
  `detail`           VARCHAR(500)    NULL     COMMENT '상세 내용',
  `reviewed_at`      DATETIME        NULL     COMMENT '운영자 검토 시각',
  `decision`         VARCHAR(10)     NULL     COMMENT '처리 결과(CONFIRM/DISMISS)',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신고 시각',
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blocks_pair` (`reporter_user_id`, `target_user_id`),
  KEY `idx_blocks_target` (`target_user_id`),
  CONSTRAINT `fk_blocks_reporter` FOREIGN KEY (`reporter_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_blocks_target`   FOREIGN KEY (`target_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_blocks_round`    FOREIGN KEY (`round_id`) REFERENCES `match_rounds` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='매칭 신고·차단';

-- ------------------------------------------------------------
-- 4. 커뮤니티 (응원 메시지 · 분실물 · 콘텐츠 신고)
-- ------------------------------------------------------------
CREATE TABLE `cheers` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '응원 메시지 ID',
  `content`           VARCHAR(40)     NOT NULL COMMENT '메시지 내용',
  `display_name`      VARCHAR(20)     NOT NULL COMMENT '자동 생성 닉네임',
  `writer_key_hash`   VARCHAR(64)     NULL     COMMENT '익명 키 해시(SHA-256)',
  `moderation_status` VARCHAR(10)     NOT NULL DEFAULT 'PASSED' COMMENT '필터 상태(PASSED/SKIPPED)',
  `report_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '신고 누적 수',
  `is_hidden`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '숨김 여부',
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 시각',
  `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_cheers_created` (`created_at`),
  KEY `idx_cheers_writer` (`writer_key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='응원 메시지(비로그인 작성)';

CREATE TABLE `lost_items` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '분실물 게시 ID',
  `kind`              VARCHAR(5)      NOT NULL COMMENT '구분(FOUND/LOST)',
  `description`       VARCHAR(100)    NOT NULL COMMENT '물품 설명',
  `place_text`        VARCHAR(50)     NOT NULL COMMENT '발견·분실 장소',
  `occurred_at`       DATETIME        NULL     COMMENT '발견·분실 시각',
  `status`            VARCHAR(10)     NOT NULL DEFAULT 'OPEN' COMMENT '상태(OPEN/RESOLVED)',
  `display_name`      VARCHAR(20)     NOT NULL COMMENT '자동 생성 닉네임',
  `author_user_id`    BIGINT UNSIGNED NULL     COMMENT '작성자 회원 ID(운영자 등록 시 운영자 ID)',
  `is_official`       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '운영자 등록 여부',
  `moderation_status` VARCHAR(10)     NOT NULL DEFAULT 'PASSED' COMMENT '필터 상태(PASSED/SKIPPED)',
  `report_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '신고 누적 수',
  `is_hidden`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '숨김 여부',
  `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 시각',
  `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_lost_status` (`status`, `created_at`),
  CONSTRAINT `fk_lost_user` FOREIGN KEY (`author_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='분실물 게시';

CREATE TABLE `content_reports` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '콘텐츠 신고 ID',
  `target_type`      VARCHAR(10)     NOT NULL COMMENT '대상 유형(CHEER/LOST_ITEM)',
  `target_id`        BIGINT UNSIGNED NOT NULL COMMENT '대상 게시물 ID',
  `reporter_user_id` BIGINT UNSIGNED NULL     COMMENT '신고자 회원 ID',
  `reason`           VARCHAR(20)     NOT NULL COMMENT '사유',
  `reviewed_at`      DATETIME        NULL     COMMENT '운영자 검토 시각',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '신고 시각',
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_once` (`target_type`, `target_id`, `reporter_user_id`),
  KEY `idx_report_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_report_user` FOREIGN KEY (`reporter_user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='콘텐츠 신고';

-- ------------------------------------------------------------
-- 5. 운영 콘텐츠 (공지 · 축제 사진)
-- ------------------------------------------------------------
CREATE TABLE `notices` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '공지 ID',
  `title`      VARCHAR(100)    NOT NULL COMMENT '제목',
  `body`       TEXT            NOT NULL COMMENT '본문',
  `is_banner`  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '긴급 배너 노출 여부',
  `created_by` BIGINT UNSIGNED NULL     COMMENT '작성 운영자 ID',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '작성 시각',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_notices_created` (`created_at`),
  CONSTRAINT `fk_notices_admin` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공지';

CREATE TABLE `festival_photos` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '축제 사진 ID',
  `image_url`     VARCHAR(500)    NOT NULL COMMENT '리사이즈 이미지 URL',
  `original_url`  VARCHAR(500)    NOT NULL COMMENT '원본 이미지 URL',
  `thumbnail_url` VARCHAR(500)    NOT NULL COMMENT '썸네일 URL',
  `caption`       VARCHAR(60)     NULL     COMMENT '캡션',
  `category`      VARCHAR(10)     NOT NULL COMMENT '카테고리(STAGE/BOOTH/SCENE/CAMPUS)',
  `place_id`      BIGINT UNSIGNED NULL     COMMENT '연결 장소 ID',
  `club_id`       BIGINT UNSIGNED NULL     COMMENT '연결 동아리 ID',
  `sort_order`    INT             NOT NULL DEFAULT 0 COMMENT '표시 순서',
  `uploaded_by`   BIGINT UNSIGNED NULL     COMMENT '업로드 운영자 ID',
  `is_hidden`     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '숨김 여부',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '업로드 시각',
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`id`),
  KEY `idx_photos_category` (`category`, `sort_order`),
  CONSTRAINT `fk_photos_place` FOREIGN KEY (`place_id`) REFERENCES `places` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_photos_club`  FOREIGN KEY (`club_id`) REFERENCES `clubs` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_photos_admin` FOREIGN KEY (`uploaded_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='축제 사진';

-- ------------------------------------------------------------
-- 6. 설정
-- ------------------------------------------------------------
CREATE TABLE `app_settings` (
  `setting_key`   VARCHAR(50)  NOT NULL COMMENT '설정 키',
  `setting_value` VARCHAR(500) NOT NULL COMMENT '설정 값',
  `description`   VARCHAR(100) NULL     COMMENT '설명',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='앱 설정';
