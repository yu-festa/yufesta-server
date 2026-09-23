package com.yufesta.domain.match.repository;

/**
 * 회원 id 두 개짜리 JPQL 프로젝션. 방향 정규화는 서비스가 UserPair.of로 한다
 */
public record UserIdPair(Long userId, Long otherUserId) {
}
