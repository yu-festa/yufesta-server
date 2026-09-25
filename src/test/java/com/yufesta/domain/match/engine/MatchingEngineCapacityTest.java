package com.yufesta.domain.match.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "미매칭자가 얼마나 생기나"를 성비별로 실측한다.
 * <p>엔진은 다수 측 1명당 파트너 1명, 소수 측 1명당 최대 N명이므로 수용량은 {@code 소수 × N}이다.
 * 따라서 제외 쌍이 없다면 미매칭 = {@code max(0, 다수 − 소수 × N)}이고, 성비가 N:1 이내면 전원 매칭이다.
 * 이 테스트가 그 식을 고정해 두므로 이월(FR-MT-03) 규모를 추정할 수 있다
 */
class MatchingEngineCapacityTest {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineCapacityTest.class);
    private static final int MAX_PARTNERS = 3;

    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void 성비가_N대1_이내면_전원_매칭이고_초과분만_미매칭이다() {
        // 총 600명을 성비만 바꿔 가며 돌린다. 기대 미매칭 = max(0, 다수 − 소수 × 3)
        List<int[]> ratios = List.of(
                new int[] {300, 300},  // 1 : 1
                new int[] {400, 200},  // 2 : 1
                new int[] {450, 150},  // 3 : 1  (수용량 경계)
                new int[] {480, 120},  // 4 : 1
                new int[] {540, 60}    // 9 : 1
        );

        log.info("성비별 미매칭 실측 (N={})", MAX_PARTNERS);
        for (int[] ratio : ratios) {
            int men = ratio[0];
            int women = ratio[1];
            List<Candidate> pool = pool(men, women);

            List<MatchResult> results = engine.match(pool, Set.of(), weights(), MAX_PARTNERS);

            int majority = Math.max(men, women);
            int minority = Math.min(men, women);
            int expectedUnmatched = Math.max(0, majority - minority * MAX_PARTNERS);
            int unmatched = pool.size() - matchedApplicationIds(results).size();
            log.info("남 {} : 여 {} → 쌍 {}, 미매칭 {}", men, women, results.size(), unmatched);

            assertThat(unmatched).isEqualTo(expectedUnmatched);
            // 소수 측은 제외 쌍이 없으면 언제나 전원 배정된다(1차에서 한 명씩 받기 때문)
            assertThat(results.stream().map(MatchResult::applicationId).distinct().count()).isEqualTo(minority);
        }
    }

    @Test
    void 소수_측은_N명까지_다수_측은_1명까지만_배정된다() {
        List<Candidate> pool = pool(100, 20);

        List<MatchResult> results = engine.match(pool, Set.of(), weights(), MAX_PARTNERS);

        // applicationId = 소수(여성) 측, partnerApplicationId = 다수(남성) 측
        assertThat(countBy(results, MatchResult::applicationId).values()).allSatisfy(count ->
                assertThat(count).isBetween(1L, (long) MAX_PARTNERS));
        assertThat(countBy(results, MatchResult::partnerApplicationId).values()).allSatisfy(count ->
                assertThat(count).isEqualTo(1L));
        // 수용량(20 × 3 = 60)만큼만 붙는다
        assertThat(results).hasSize(60);
    }

    @Test
    void 이전_회차에_매칭된_쌍이_모두_제외되면_그만큼_다시_붙지_못한다() {
        // 여 2 : 남 2에서 가능한 4쌍 중 3쌍을 막으면 남는 조합은 하나뿐이라 각 1명씩만 매칭된다
        List<Candidate> pool = pool(2, 2);
        Set<UserPair> excluded = Set.of(
                UserPair.of(userId(1), userId(3)),
                UserPair.of(userId(2), userId(3)),
                UserPair.of(userId(2), userId(4))
        );

        List<MatchResult> results = engine.match(pool, excluded, weights(), MAX_PARTNERS);

        assertThat(results).hasSize(1);
        assertThat(pool.size() - matchedApplicationIds(results).size()).isEqualTo(2);
    }

    // 남성 먼저, 그다음 여성. 태그·나이대는 점수만 흔들고 배정 수에는 영향이 없어 단순하게 둔다
    private static List<Candidate> pool(int men, int women) {
        List<Candidate> pool = new ArrayList<>(men + women);
        for (int i = 1; i <= men + women; i++) {
            pool.add(Candidate.builder()
                    .applicationId((long) i)
                    .userId(userId(i))
                    .gender(i <= men ? Gender.M : Gender.F)
                    .tags(Set.of(MatchTag.values()[i % MatchTag.values().length]))
                    .ageBand(null)
                    .entryType(EntryType.NEW)
                    .build());
        }
        return pool;
    }

    private static long userId(int index) {
        return 1000L + index;
    }

    private static Set<Long> matchedApplicationIds(List<MatchResult> results) {
        Set<Long> ids = results.stream().map(MatchResult::applicationId).collect(Collectors.toSet());
        ids.addAll(results.stream().map(MatchResult::partnerApplicationId).toList());
        return ids;
    }

    private static java.util.Map<Long, Long> countBy(
            List<MatchResult> results,
            java.util.function.Function<MatchResult, Long> key
    ) {
        return results.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
    }

    private static MatchWeights weights() {
        return MatchWeights.builder()
                .tag(new BigDecimal("1"))
                .slot(new BigDecimal("2"))
                .ageSame(new BigDecimal("1"))
                .ageAdjacent(new BigDecimal("0.5"))
                .build();
    }
}
