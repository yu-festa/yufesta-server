-- ============================================================
-- V4: 라인업 초기 데이터. 동아리가 제공한 자료 9건과 타임테이블 CLUB 공연 9건 연결.
-- 대표 사진(photo_url)은 업로드 API가 생긴 뒤 운영자가 넣는다. 수정은 운영자 API(admin/clubs)로.
-- docs/erd.sql 하단과 같은 내용을 유지한다.
-- ============================================================

INSERT INTO `clubs` (`name`, `intro`, `genre`, `signature_song`, `instagram_url`, `sort_order`, `created_at`, `updated_at`) VALUES
  ('신명마당',     '신명나게 놀아보세~!',                               '풍물놀이', '조일환류북춤',                'https://www.instagram.com/sinmyeong.madang',    1, NOW(), NOW()),
  ('천마응원단',   '무대 위 가장 빛나는 순간 천마응원단',               '응원단',   '신해철-그대에게',             'https://www.instagram.com/chunma_cheerteam_28', 2, NOW(), NOW()),
  ('HIPCOM',       '영남대학교 유일 힙합 동아리 HIPCOM',                '힙합',     '최준현-거북당',               'https://www.instagram.com/hipcom_yu',           3, NOW(), NOW()),
  ('코스모스',     '청춘.',                                             '락밴드',   '검정치마-Hollywood',          'https://www.instagram.com/groupsound_cosmos',   4, NOW(), NOW()),
  ('The WE',       '음악을 사랑하는 모든 이들을 환영합니다',            '락',       'QWER-고민중독',               'https://www.instagram.com/we_are_thewe',        5, NOW(), NOW()),
  ('ECHOES',       '다양한 장르에 도전하며 성장하는 청춘들의 울림',     '락 밴드',  '터치드 - 야경',               'https://www.instagram.com/band_echoes',         6, NOW(), NOW()),
  ('예사가락',     'Yes, I got Rock.',                                  '밴드',     'SPYAIR - Some Like it Hot!!', 'https://www.instagram.com/yesagarak_official',  7, NOW(), NOW()),
  ('BLUEWAVE',     '무대 위에 파랑을 일으키는 밴드 블루웨이브 입니다!', '밴드',     '버즈-나에게로 떠나는 여행',   'https://www.instagram.com/bluewave_1981',       8, NOW(), NOW()),
  ('MAX & ZENITH', '영남대 유일무이 댄스 동아리 Max&Zenith 입니다!',    '댄스',     'aespa - lemonade',            'https://www.instagram.com/max_n_zenith',        9, NOW(), NOW());

-- 타임테이블 CLUB 공연에 연결. 공연명(총동연 표기)과 동아리명(동아리 표기)이 다른 COSMOS·BLUE WAVE는 여기서 맞춘다
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = '신명마당'     ORDER BY `id` LIMIT 1) WHERE `title` = '신명마당'     AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = '천마응원단'   ORDER BY `id` LIMIT 1) WHERE `title` = '천마응원단'   AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = 'HIPCOM'       ORDER BY `id` LIMIT 1) WHERE `title` = 'HIPCOM'       AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = '코스모스'     ORDER BY `id` LIMIT 1) WHERE `title` = 'COSMOS'       AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = 'The WE'       ORDER BY `id` LIMIT 1) WHERE `title` = 'The WE'       AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = 'ECHOES'       ORDER BY `id` LIMIT 1) WHERE `title` = 'ECHOES'       AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = '예사가락'     ORDER BY `id` LIMIT 1) WHERE `title` = '예사가락'     AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = 'BLUEWAVE'     ORDER BY `id` LIMIT 1) WHERE `title` = 'BLUE WAVE'    AND `slot_type` = 'CLUB';
UPDATE `timetable_slots` SET `club_id` = (SELECT `id` FROM `clubs` WHERE `name` = 'MAX & ZENITH' ORDER BY `id` LIMIT 1) WHERE `title` = 'MAX & ZENITH' AND `slot_type` = 'CLUB';
