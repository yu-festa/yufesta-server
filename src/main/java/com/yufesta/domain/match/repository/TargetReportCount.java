package com.yufesta.domain.match.repository;

/**
 * 대상 회원별 유효 신고 수(기각 제외) JPQL 프로젝션. 운영자 검토 목록에 붙인다
 */
public record TargetReportCount(Long targetUserId, long count) {
}
