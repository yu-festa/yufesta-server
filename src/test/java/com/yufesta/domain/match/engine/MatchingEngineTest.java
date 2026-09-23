package com.yufesta.domain.match.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

    // app_settings 초기값과 같은 가중치
    private static final MatchWeights WEIGHTS = MatchWeights.builder()
            .tag(new BigDecimal("1"))
            .slot(new BigDecimal("2"))
            .ageSame(new BigDecimal("1"))
            .ageAdjacent(new BigDecimal("0.5"))
            .build();
    private static final int N = 3;

    private final MatchingEngine engine = new MatchingEngine();

    @Test
    void 남3_여3이면_전원_1층에서_1대1로_매칭된다() {
        List<Candidate> pool = concat(women(3), men(3));

        List<MatchResult> results = engine.match(pool, Set.of(), WEIGHTS, N);

        assertThat(results).hasSize(3);
        assertThat(results).allMatch(r -> r.pass() == 1);
        assertThat(results).extracting(MatchResult::applicationId).doesNotHaveDuplicates();
        assertThat(results).extracting(MatchResult::partnerApplicationId).doesNotHaveDuplicates();
    }

    @Test
    void 여30_남50이면_전원_매칭되고_여성_20명은_2명_10명은_1명이다() {
        List<MatchResult> results = engine.match(concat(women(30), men(50)), Set.of(), WEIGHTS, N);

        assertThat(results).hasSize(50);
        assertThat(results).extracting(MatchResult::partnerApplicationId).doesNotHaveDuplicates();
        Map<Long, Long> perWoman = partnerCounts(results);
        assertThat(perWoman.values().stream().filter(c -> c == 2).count()).isEqualTo(20);
        assertThat(perWoman.values().stream().filter(c -> c == 1).count()).isEqualTo(10);
        assertThat(results.stream().filter(r -> r.pass() == 1).count()).isEqualTo(30);
    }

    @Test
    void 여30_남130이면_여성은_정확히_3명씩_받고_남40명이_미매칭이다() {
        List<MatchResult> results = engine.match(concat(women(30), men(130)), Set.of(), WEIGHTS, N);

        assertThat(results).hasSize(90);
        assertThat(partnerCounts(results).values()).hasSize(30).allMatch(c -> c == 3);
        assertThat(results.stream().filter(r -> r.pass() == 1).count()).isEqualTo(30);
        assertThat(results.stream().filter(r -> r.pass() == 2).count()).isEqualTo(60);
    }

    @Test
    void 자리가_충분하고_제외가_없으면_미매칭이_없다() {
        List<MatchResult> results = engine.match(concat(women(5), men(15)), Set.of(), WEIGHTS, N);

        assertThat(results).hasSize(15);
        assertThat(partnerCounts(results).values()).hasSize(5).allMatch(c -> c == 3);
    }

    @Test
    void 제외_쌍은_점수가_가장_높아도_어느_층에서도_매칭되지_않는다() {
        Candidate woman = candidate(1L, 101L, Gender.F, Set.of(MatchTag.MUSIC, MatchTag.CAFE), 9L, AgeBand.A22_24, EntryType.NEW);
        Candidate soulmate = candidate(2L, 102L, Gender.M, Set.of(MatchTag.MUSIC, MatchTag.CAFE), 9L, AgeBand.A22_24, EntryType.NEW);
        Candidate other = candidate(3L, 103L, Gender.M, Set.of(), null, null, EntryType.NEW);
        Set<UserPair> excluded = Set.of(UserPair.of(102L, 101L));

        List<MatchResult> results = engine.match(List.of(woman, soulmate, other), excluded, WEIGHTS, N);

        assertThat(results).extracting(MatchResult::partnerApplicationId).containsExactly(3L);
    }

    @Test
    void 점수식은_공통_태그_공연_나이대를_더한_값이다() {
        Candidate a = candidate(1L, 1L, Gender.F, Set.of(MatchTag.MUSIC, MatchTag.CAFE, MatchTag.PET), 7L, AgeBand.A22_24, EntryType.NEW);
        Candidate b = candidate(2L, 2L, Gender.M, Set.of(MatchTag.MUSIC, MatchTag.CAFE, MatchTag.GAME), 7L, AgeBand.A25_27, EntryType.NEW);
        Candidate noSlot = candidate(3L, 3L, Gender.M, Set.of(MatchTag.MUSIC), null, AgeBand.A22_24, EntryType.NEW);
        Candidate noAge = candidate(4L, 4L, Gender.M, Set.of(), 7L, null, EntryType.NEW);

        assertThat(MatchingEngine.score(a, b, WEIGHTS)).isEqualByComparingTo("4.50");   // 태그 2 + 공연 2 + 인접 0.5
        assertThat(MatchingEngine.score(a, noSlot, WEIGHTS)).isEqualByComparingTo("2.00"); // 태그 1 + 동일 1
        assertThat(MatchingEngine.score(a, noAge, WEIGHTS)).isEqualByComparingTo("2.00");  // 공연 2
    }

    @Test
    void 동점이면_신청_id가_작은_쌍이_먼저이고_같은_입력은_같은_결과다() {
        List<Candidate> pool = concat(women(2), men(2)); // 태그 없음 → 전부 0점
        List<MatchResult> first = engine.match(pool, Set.of(), WEIGHTS, N);
        List<MatchResult> second = engine.match(new ArrayList<>(pool), new HashSet<>(), WEIGHTS, N);

        assertThat(first).isEqualTo(second);
        assertThat(first.get(0).applicationId()).isEqualTo(1L);
        assertThat(first.get(0).partnerApplicationId()).isEqualTo(1001L);
    }

    @Test
    void 한쪽_성별이_없으면_빈_결과다() {
        assertThat(engine.match(women(5), Set.of(), WEIGHTS, N)).isEmpty();
        assertThat(engine.match(List.of(), Set.of(), WEIGHTS, N)).isEmpty();
    }

    @Test
    void 자리가_하나_남으면_점수가_낮아도_이월자가_신규보다_먼저_배정된다() {
        Candidate woman = candidate(1L, 1L, Gender.F, Set.of(MatchTag.MUSIC), null, null, EntryType.NEW);
        Candidate newcomer = candidate(2L, 2L, Gender.M, Set.of(MatchTag.MUSIC), null, null, EntryType.NEW);   // 1점
        Candidate carried = candidate(3L, 3L, Gender.M, Set.of(), null, null, EntryType.CARRIED);            // 0점

        List<MatchResult> results = engine.match(List.of(woman, newcomer, carried), Set.of(), WEIGHTS, 1);

        assertThat(results).extracting(MatchResult::partnerApplicationId).containsExactly(3L);
    }

    @Test
    void 이월자가_없으면_첫_층은_점수순_1대1_그리디와_같다() {
        Random random = new Random(7);
        List<Candidate> pool = concat(randomCandidates(20, Gender.F, 1L, random), randomCandidates(25, Gender.M, 1001L, random));

        List<MatchResult> firstTier = engine.match(pool, Set.of(), WEIGHTS, N).stream()
                .filter(r -> r.pass() == 1)
                .toList();

        assertThat(firstTier).containsExactlyInAnyOrderElementsOf(referenceGreedy(pool));
    }

    @Test
    void 남300_여200은_1초_안에_끝난다() {
        Random random = new Random(42);
        List<Candidate> pool = concat(randomCandidates(200, Gender.F, 1L, random), randomCandidates(300, Gender.M, 1001L, random));

        List<MatchResult> results = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> engine.match(pool, Set.of(), WEIGHTS, N));

        assertThat(results).hasSize(300);
    }

    // 1:1 그리디를 가장 단순하게 따로 구현한 기준값
    private static List<MatchResult> referenceGreedy(List<Candidate> pool) {
        List<Candidate> women = pool.stream().filter(c -> c.gender() == Gender.F).toList();
        List<Candidate> men = pool.stream().filter(c -> c.gender() == Gender.M).toList();
        record Pair(Candidate w, Candidate m, BigDecimal score) {
        }
        List<Pair> pairs = new ArrayList<>();
        for (Candidate w : women) {
            for (Candidate m : men) {
                pairs.add(new Pair(w, m, MatchingEngine.score(w, m, WEIGHTS)));
            }
        }
        pairs.sort((x, y) -> {
            int byScore = y.score().compareTo(x.score());
            if (byScore != 0) {
                return byScore;
            }
            int byLow = Long.compare(Math.min(x.w().applicationId(), x.m().applicationId()), Math.min(y.w().applicationId(), y.m().applicationId()));
            return byLow != 0 ? byLow : Long.compare(Math.max(x.w().applicationId(), x.m().applicationId()), Math.max(y.w().applicationId(), y.m().applicationId()));
        });
        Set<Long> used = new HashSet<>();
        List<MatchResult> results = new ArrayList<>();
        for (Pair p : pairs) {
            if (used.contains(p.w().applicationId()) || used.contains(p.m().applicationId())) {
                continue;
            }
            used.add(p.w().applicationId());
            used.add(p.m().applicationId());
            results.add(new MatchResult(p.w().applicationId(), p.m().applicationId(), p.score(), 1));
        }
        return results;
    }

    private static Map<Long, Long> partnerCounts(List<MatchResult> results) {
        return results.stream().collect(Collectors.groupingBy(MatchResult::applicationId, Collectors.counting()));
    }

    // 태그 없는 여성 n명: 신청 id 1..n
    private static List<Candidate> women(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> candidate((long) i, (long) i, Gender.F, Set.of(), null, null, EntryType.NEW))
                .toList();
    }

    // 태그 없는 남성 n명: 신청 id 1001..1000+n
    private static List<Candidate> men(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> candidate(1000L + i, 1000L + i, Gender.M, Set.of(), null, null, EntryType.NEW))
                .toList();
    }

    private static List<Candidate> randomCandidates(int n, Gender gender, long firstId, Random random) {
        MatchTag[] tags = MatchTag.values();
        AgeBand[] bands = AgeBand.values();
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Set<MatchTag> picked = new HashSet<>();
            int count = random.nextInt(4);
            while (picked.size() < count) {
                picked.add(tags[random.nextInt(tags.length)]);
            }
            Long slot = random.nextBoolean() ? (long) (random.nextInt(5) + 1) : null;
            AgeBand band = random.nextInt(5) == 0 ? null : bands[random.nextInt(bands.length)];
            list.add(candidate(firstId + i, firstId + i, gender, picked, slot, band, EntryType.NEW));
        }
        return list;
    }

    private static List<Candidate> concat(List<Candidate> a, List<Candidate> b) {
        List<Candidate> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static Candidate candidate(
            Long applicationId, Long userId, Gender gender, Set<MatchTag> tags, Long slot, AgeBand band, EntryType type
    ) {
        return Candidate.builder()
                .applicationId(applicationId).userId(userId).gender(gender)
                .tags(tags).wantedSlotId(slot).ageBand(band).entryType(type)
                .build();
    }
}
