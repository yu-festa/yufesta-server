package com.yufesta.domain.match.engine;

import java.math.BigDecimal;

/**
 * 엔진 출력 한 쌍. applicationId는 소수 측(여러 명을 받는 쪽), partnerApplicationId는 다수 측.
 * pass는 소수 측의 첫 파트너면 1, 추가 파트너면 2(ERD matches.assign_pass). 양방향 2행 저장은 호출 측이 한다
 */
public record MatchResult(
        Long applicationId,
        Long partnerApplicationId,
        BigDecimal score,
        int pass
) {
}
