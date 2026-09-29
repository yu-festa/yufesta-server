-- ============================================================
-- ⚠ QA에서 쓴 게시물을 지운다: 응원 메시지, 분실물(이미지·댓글 포함), 콘텐츠 신고.
-- 인스타팅은 건드리지 않는다(qa-reset.sql). 공지·라인업·타임테이블·사진처럼 운영자가 넣은 콘텐츠도 건드리지 않는다.
-- 시험용 공지는 운영자 화면에서 직접 지운다(어느 것이 시험용인지 SQL로는 알 수 없다).
--
--   ./load/dbsql.sh load/qa-content-cleanup.sql
--
-- 최종 배포(10/1) 직전에 한 번 실행한다. 실제 사용자가 글을 쓰기 시작한 뒤에는 실행하지 않는다.
-- 분실물에 첨부한 이미지 파일은 S3에 남는다(용량이 작아 축제 뒤 버킷을 지울 때 함께 정리한다).
-- ============================================================

SELECT '=== before ===' AS report;
SELECT (SELECT COUNT(*) FROM cheers) AS cheers,
       (SELECT COUNT(*) FROM lost_items) AS lost_items,
       (SELECT COUNT(*) FROM lost_item_comments) AS lost_item_comments,
       (SELECT COUNT(*) FROM content_reports) AS content_reports;

DELETE FROM content_reports;
DELETE FROM cheers;
-- 이미지·댓글·댓글 별칭은 FK ON DELETE CASCADE로 함께 지워진다
DELETE FROM lost_items;

SELECT '=== after ===' AS report;
SELECT (SELECT COUNT(*) FROM cheers) AS cheers,
       (SELECT COUNT(*) FROM lost_items) AS lost_items,
       (SELECT COUNT(*) FROM lost_item_comments) AS lost_item_comments,
       (SELECT COUNT(*) FROM content_reports) AS content_reports;
