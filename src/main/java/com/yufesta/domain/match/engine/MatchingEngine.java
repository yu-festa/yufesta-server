package com.yufesta.domain.match.engine;

import com.yufesta.domain.match.enums.Gender;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 인스타팅 매칭 계산기(SRS 부록 A). 스프링·DB에 의존하지 않는 순수 클래스.
 * <p>규칙 하나로 배정한다: 후보 쌍을 (다수 측 이월자 우선 → 점수 높은 순 → 신청 id 작은 순)으로 정렬해 두고,
 * 층 t = 0..N-1 순서로 "다수 측 미배정 + 소수 측 파트너 수 == t"인 쌍을 붙인다.
 * 층 0이 SRS의 1차(전원 1:1), 그 뒤가 2차(라운드 로빈 추가 배정)에 해당한다.
 * 같은 입력이면 항상 같은 출력이다
 */
public class MatchingEngine {

    private static final int SCORE_SCALE = 2;

    /**
     * 후보들을 짝지어 결과 쌍 목록을 돌려준다.
     * @param candidates    풀(취소·차단 회원은 호출 측이 이미 제외)
     * @param excludedPairs 붙이면 안 되는 회원 쌍(차단 양방향, 이전 회차 매칭)
     * @param weights       점수 가중치
     * @param maxPartners   소수 측 1인당 최대 파트너 수 N(≥ 1)
     */
    public List<MatchResult> match(
            List<Candidate> candidates,
            Set<UserPair> excludedPairs,
            MatchWeights weights,
            int maxPartners
    ) {
        if (maxPartners < 1) {
            throw new IllegalArgumentException("maxPartners는 1 이상이어야 한다");
        }
        List<Candidate> men = candidates.stream().filter(c -> c.gender() == Gender.M).toList();
        List<Candidate> women = candidates.stream().filter(c -> c.gender() == Gender.F).toList();
        if (men.isEmpty() || women.isEmpty()) {
            return List.of();
        }

        // 인원이 많은 쪽이 다수(각자 1명), 적은 쪽이 소수(최대 N명). 동수면 남성을 다수로 본다
        boolean menAreMajority = men.size() >= women.size();
        List<Candidate> majority = menAreMajority ? men : women;
        List<Candidate> minority = menAreMajority ? women : men;

        List<ScoredPair> pairs = scoreAllPairs(minority, majority, excludedPairs, weights);
        return assign(pairs, majority.size(), maxPartners);
    }

    // 이성 쌍 전부 점수화. 제외 쌍은 목록에 넣지 않아 어느 층에서도 붙지 않는다
    private List<ScoredPair> scoreAllPairs(
            List<Candidate> minority,
            List<Candidate> majority,
            Set<UserPair> excludedPairs,
            MatchWeights weights
    ) {
        List<ScoredPair> pairs = new ArrayList<>(minority.size() * majority.size());
        for (Candidate minor : minority) {
            for (Candidate major : majority) {
                if (excludedPairs.contains(UserPair.of(minor.userId(), major.userId()))) {
                    continue;
                }
                pairs.add(new ScoredPair(minor, major, score(minor, major, weights)));
            }
        }
        pairs.sort(PAIR_ORDER);
        return pairs;
    }

    // 층 순서로 훑는다. 층 안에서는 정렬 순서가 곧 우선순위라 라운드 로빈이 자연히 성립한다
    private List<MatchResult> assign(List<ScoredPair> pairs, int majoritySize, int maxPartners) {
        Map<Long, Integer> partnerCount = new HashMap<>();
        Set<Long> assignedMajority = new HashSet<>();
        List<MatchResult> results = new ArrayList<>();

        for (int tier = 0; tier < maxPartners && assignedMajority.size() < majoritySize; tier++) {
            for (ScoredPair pair : pairs) {
                Long minorId = pair.minor().applicationId();
                if (assignedMajority.contains(pair.major().applicationId())
                        || partnerCount.getOrDefault(minorId, 0) != tier) {
                    continue;
                }
                assignedMajority.add(pair.major().applicationId());
                partnerCount.merge(minorId, 1, Integer::sum);
                results.add(new MatchResult(minorId, pair.major().applicationId(), pair.score(), tier == 0 ? 1 : 2));
            }
        }
        return results;
    }

    // 점수 = 공통 태그 수 × tag + 같은 공연 × slot + 나이대 동일 × ageSame 또는 인접 × ageAdjacent
    static BigDecimal score(Candidate a, Candidate b, MatchWeights weights) {
        long commonTags = a.tags().stream().filter(b.tags()::contains).count();
        BigDecimal score = weights.tag().multiply(BigDecimal.valueOf(commonTags));

        if (a.wantedSlotId() != null && a.wantedSlotId().equals(b.wantedSlotId())) {
            score = score.add(weights.slot());
        }
        if (a.ageBand() != null && b.ageBand() != null) {
            if (a.ageBand() == b.ageBand()) {
                score = score.add(weights.ageSame());
            } else if (a.ageBand().isAdjacentTo(b.ageBand())) {
                score = score.add(weights.ageAdjacent());
            }
        }
        return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    // 정렬 키: 다수 측 이월자 우선(FR-MT-23) → 점수 내림차순 → 작은 신청 id → 큰 신청 id (결정성)
    private static final Comparator<ScoredPair> PAIR_ORDER = Comparator
            .comparing((ScoredPair p) -> !p.major().isCarried())
            .thenComparing(ScoredPair::score, Comparator.reverseOrder())
            .thenComparingLong(p -> Math.min(p.minor().applicationId(), p.major().applicationId()))
            .thenComparingLong(p -> Math.max(p.minor().applicationId(), p.major().applicationId()));

    private record ScoredPair(Candidate minor, Candidate major, BigDecimal score) {
    }
}
