package com.yufesta.domain.match.engine;

import java.math.BigDecimal;
import lombok.Builder;

/**
 * 점수 가중치(부록 A). 값은 app_settings의 match.weight.*를 호출 측이 읽어 넘긴다
 */
@Builder
public record MatchWeights(
        BigDecimal tag,
        BigDecimal slot,
        BigDecimal ageSame,
        BigDecimal ageAdjacent
) {
}
