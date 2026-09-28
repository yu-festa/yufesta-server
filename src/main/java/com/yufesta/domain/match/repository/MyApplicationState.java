package com.yufesta.domain.match.repository;

/**
 * 한 회차에서의 내 신청 상태 JPQL 프로젝션. 행이 있으면 그 회차에 유효한(취소하지 않은) 신청이 있다는 뜻이다.
 * 홈 요약의 my를 쿼리 하나로 만들려고 둔다(신청 엔티티를 통째로 읽을 필요가 없다)
 */
public record MyApplicationState(Long roundId, boolean matched) {
}
