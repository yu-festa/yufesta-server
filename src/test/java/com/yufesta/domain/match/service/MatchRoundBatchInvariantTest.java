package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.appsetting.entity.AppSetting;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.engine.MatchWeights;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.BlockReason;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.repository.PlaceRepository;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.repository.TimetableSlotRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 전체(풀 조회 → 엔진 → 저장 → 발표 → 이월 → 다음 회차)가 SRS 규칙을 지키는지 1,000명 규모로 검증한다.
 * <p>단위 테스트(MatchingEngineTest)는 엔진만, 서비스 테스트(MatchRoundBatchServiceTest)는 mock으로 흐름만 본다.
 * 여기서는 실제 스프링 컨텍스트와 DB를 써서 "저장된 결과"가 규칙을 만족하는지 확인한다. 축제 당일에 처음 발견하지 않기 위한 그물이다.
 * <p>시드는 고정 난수(SEED)라 실패하면 그대로 재현된다. 같은 불변식을 운영 DB에서 확인하는 SQL은 load/verify.sql
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 시드·배치·검증을 한 트랜잭션에서 끝내고 끝나면 롤백한다(테스트끼리 데이터가 섞이지 않는다)
class MatchRoundBatchInvariantTest {

    private static final Logger log = LoggerFactory.getLogger(MatchRoundBatchInvariantTest.class);
    private static final long SEED = 42L;
    private static final int MAX_PARTNERS = 3;
    private static final LocalDateTime FESTIVAL = LocalDateTime.of(2026, 10, 2, 0, 0);

    @Autowired
    private MatchRoundBatchService batchService;

    @Autowired
    private MatchRoundRepository matchRoundRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TimetableSlotRepository timetableSlotRepository;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private AppSettingReader appSettingReader;

    @Autowired
    private EntityManager em;

    private Random random;
    private MatchRound round1;
    private MatchRound round2;
    private List<TimetableSlot> slots;

    @BeforeEach
    void setUp() {
        // 테스트마다 커밋이 남으므로 FK 순서대로 비우고 다시 시드한다
        for (String table : List.of(
                "matches", "blocks", "application_tags", "applications",
                "timetable_slots", "places", "users", "match_rounds", "app_settings")) {
            em.createNativeQuery("delete from " + table).executeUpdate();
        }
        seedSettings();
        appSettingReader.invalidate(); // 30초 캐시가 이전 테스트 값을 들고 있지 않도록
        random = new Random(SEED);
        round1 = matchRoundRepository.save(round(1, FESTIVAL.withHour(15).withMinute(50), FESTIVAL.withHour(16)));
        round2 = matchRoundRepository.save(round(2, FESTIVAL.withHour(19).withMinute(50), FESTIVAL.withHour(20)));
        round1.open();
        slots = seedSlots();
    }

    @Test
    @DisplayName("1,000명 배치 결과가 모든 불변식을 만족한다")
    void 천명_배치_결과는_거울행_성별_차단_상한_점수_결정성을_모두_만족한다() {
        // 성비 550:450, 차단 30쌍. 수용량(450 × 3)이 충분해 미매칭은 차단에 막힌 사람만 나온다
        List<Application> pool = seedApplications(round1, 550, 450);
        List<Block> blocks = seedBlocks(pool, 30);

        long startedAt = System.nanoTime();
        RoundBatchResultResponse result = batchService.close(round1.getId());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        List<Match> rows = matchRepository.findAllByRound_Id(round1.getId());
        log.info("풀 {}명(남 {}·여 {}), 쌍 {}, 1차 {}·2차 {}, 미매칭 {}, 평균 점수 {}, 소요 {}ms",
                result.poolSize(), result.poolMen(), result.poolWomen(), result.pairCount(),
                result.firstPassPairs(), result.secondPassPairs(), result.unmatchedApplicants(),
                result.averageScore(), elapsed.toMillis());

        assertMirrorRows(rows);
        assertOppositeGenderOnly(rows);
        assertNoBlockedPair(rows, blocks);
        assertPartnerLimits(rows, pool);
        assertScoresRecomputed(rows, pool);
        assertAssignPass(rows);
        // 1차는 소수 측 인원만큼(차단으로 못 붙은 사람 제외), 2차는 그 뒤 추가 배정
        assertThat(result.firstPassPairs()).isLessThanOrEqualTo(450);
        assertThat(result.pairCount()).isEqualTo(rows.size() / 2);
        // NFR-PF-03: 500명 30초. 1,000명이라도 로컬에서 한참 여유가 있어야 한다
        assertThat(elapsed).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("재실행은 같은 결과를 낸다(동점 결정성)")
    void 재실행하면_같은_쌍과_같은_점수가_나온다() {
        seedApplications(round1, 120, 80);
        batchService.close(round1.getId());
        Set<String> first = pairSignature(matchRepository.findAllByRound_Id(round1.getId()));

        batchService.rerun(round1.getId());

        List<Match> rows = matchRepository.findAllByRound_Id(round1.getId());
        assertThat(pairSignature(rows)).isEqualTo(first);
        // 지우고 다시 넣으므로 행이 중복되지 않는다
        assertThat(rows).hasSize(first.size());
    }

    @Test
    @DisplayName("발표하면 미매칭만 다음 회차로 이월되고 발표 전 공연은 비워진다")
    void 발표_후_미매칭자는_CARRIED로_복사되고_2회차에는_이전_쌍이_다시_붙지_않는다() {
        // 성비 400:100이면 수용량 300이라 100명이 반드시 미매칭된다(이월 대상 확보)
        List<Application> pool = seedApplications(round1, 400, 100);
        batchService.close(round1.getId());
        Set<Long> matchedIds = matchRepository.findMatchedApplicationIdsByRoundId(round1.getId());
        List<Application> unmatched = pool.stream().filter(a -> !matchedIds.contains(a.getId())).toList();

        batchService.publish(round1.getId());

        List<Application> carried = applicationRepository.findPoolByRoundId(round2.getId());
        log.info("1회차 미매칭 {}명 → 2회차 이월 {}건", unmatched.size(), carried.size());
        assertThat(unmatched).hasSize(100);
        assertThat(carried).hasSize(unmatched.size());
        assertThat(carried).allSatisfy(copy -> {
            assertThat(copy.getEntryType()).isEqualTo(EntryType.CARRIED);
            assertThat(copy.getSourceApplication()).isNotNull();
            // FR-MT-05: 2회차 발표(20:00) 이후 시작 공연만 따라온다
            if (copy.getWantedSlot() != null) {
                assertThat(copy.getWantedSlot().getStartAt()).isAfterOrEqualTo(round2.getPublishAt());
            }
        });
        // 원본에 있던 이른 공연은 복사본에서 비워졌다
        assertThat(unmatched.stream().filter(a -> a.getWantedSlot() != null
                && a.getWantedSlot().getStartAt().isBefore(round2.getPublishAt()))).isNotEmpty();
        assertThat(matchRoundRepository.findBySeq(2).orElseThrow().getStatus().name()).isEqualTo("OPEN");

        // 1회차에 매칭됐던 20쌍이 2회차에 다시 신청해도 서로 다시 붙지 않아야 한다(FR-MT-22)
        Set<Set<Long>> previousPairs = rejoinTwentyMatchedPairs();
        batchService.close(round2.getId());

        List<Match> round2Rows = matchRepository.findAllByRound_Id(round2.getId());
        assertThat(round2Rows).isNotEmpty();
        assertThat(userPairs(round2Rows)).doesNotContainAnyElementsOf(previousPairs);
        assertOppositeGenderOnly(round2Rows);
        assertMirrorRows(round2Rows);
    }

    @Test
    @DisplayName("무작위 20명의 매칭 결과를 사람이 읽을 수 있게 출력한다")
    void 무작위_참가자와_매칭_결과를_점수_근거와_함께_출력한다() {
        // 눈으로 확인하는 용도. 참가자 속성 → 매칭 쌍(점수 근거) → 미매칭을 순서대로 찍는다
        List<Application> pool = seedApplications(round1, 12, 8);
        MatchWeights weights = weights();

        batchService.close(round1.getId());
        List<Match> rows = matchRepository.findAllByRound_Id(round1.getId());

        log.info("── 참가자 {}명 (남 12 · 여 8)", pool.size());
        pool.stream().sorted(java.util.Comparator.comparing(Application::getId)).forEach(a ->
                log.info("  [{}] #{} 나이 {} 태그 {} 공연 {}",
                        a.getGender(), a.getId(),
                        a.getAgeBand() == null ? "-" : a.getAgeBand().value(),
                        a.getTags().isEmpty() ? "-" : tagNames(a),
                        a.getWantedSlot() == null ? "-" : a.getWantedSlot().getTitle()));

        log.info("── 매칭 결과 {}쌍", rows.size() / 2);
        Map<Long, Application> byId = pool.stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        rows.stream()
                // 쌍당 두 행이므로 소수 측(여성) 방향만 찍는다
                .filter(row -> row.getApplication().getGender() == Gender.F)
                .sorted(java.util.Comparator.comparing((Match m) -> m.getApplication().getId())
                        .thenComparing(Match::getScore, java.util.Comparator.reverseOrder()))
                .forEach(row -> {
                    Application mine = byId.get(row.getApplication().getId());
                    Application partner = byId.get(row.getPartnerApplication().getId());
                    log.info("  [{}차] #{} ↔ #{}  점수 {}  = {}",
                            row.getAssignPass(), mine.getId(), partner.getId(), row.getScore(),
                            scoreReason(mine, partner, weights));
                });

        Set<Long> matched = matchRepository.findMatchedApplicationIdsByRoundId(round1.getId());
        List<Application> unmatched = pool.stream().filter(a -> !matched.contains(a.getId())).toList();
        log.info("── 미매칭 {}명 {}", unmatched.size(),
                unmatched.stream().map(a -> "#" + a.getId() + "(" + a.getGender() + ")").toList());

        // 수용량(8 × 3 = 24)이 남성 12명보다 크므로 전원 매칭이어야 한다
        assertThat(unmatched).isEmpty();
        assertThat(rows).hasSize(24);
    }

    // --- 불변식 ---------------------------------------------------------------

    /** matches는 방향성 2행 저장이다. A→B가 있으면 B→A도 있고 점수·차수가 같다 */
    private void assertMirrorRows(List<Match> rows) {
        Map<String, Match> byDirection = rows.stream()
                .collect(Collectors.toMap(m -> m.getApplication().getId() + ">" + m.getPartnerApplication().getId(),
                        Function.identity()));
        assertThat(rows).hasSize(byDirection.size());
        assertThat(rows).allSatisfy(row -> {
            Match mirror = byDirection.get(row.getPartnerApplication().getId() + ">" + row.getApplication().getId());
            assertThat(mirror).as("거울 행 없음: %s", row.getId()).isNotNull();
            assertThat(mirror.getScore()).isEqualByComparingTo(row.getScore());
            assertThat(mirror.getAssignPass()).isEqualTo(row.getAssignPass());
        });
    }

    private void assertOppositeGenderOnly(List<Match> rows) {
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getApplication().getGender()).isNotEqualTo(row.getPartnerApplication().getGender()));
    }

    private void assertNoBlockedPair(List<Match> rows, List<Block> blocks) {
        Set<Set<Long>> blocked = blocks.stream()
                .map(block -> Set.of(block.getReporter().getId(), block.getTarget().getId()))
                .collect(Collectors.toSet());
        assertThat(userPairs(rows)).doesNotContainAnyElementsOf(blocked);
    }

    /** 다수 측은 1명까지, 소수 측은 N명까지. 어느 쪽이 소수인지는 풀 성비로 정해진다 */
    private void assertPartnerLimits(List<Match> rows, List<Application> pool) {
        long men = pool.stream().filter(a -> a.getGender() == Gender.M).count();
        Gender minority = men >= pool.size() - men ? Gender.F : Gender.M;

        Map<Long, Long> partnerCount = rows.stream()
                .collect(Collectors.groupingBy(m -> m.getApplication().getId(), Collectors.counting()));
        rows.forEach(row -> {
            long count = partnerCount.get(row.getApplication().getId());
            int limit = row.getApplication().getGender() == minority ? MAX_PARTNERS : 1;
            assertThat(count).as("파트너 수 상한 위반: 신청 %s", row.getApplication().getId()).isLessThanOrEqualTo(limit);
        });
    }

    /** 저장된 점수가 가중치 규칙(공통 태그·같은 공연·나이대)으로 다시 계산한 값과 같은지 */
    private void assertScoresRecomputed(List<Match> rows, List<Application> pool) {
        MatchWeights weights = weights();
        Map<Long, Application> byId = pool.stream().collect(Collectors.toMap(Application::getId, Function.identity()));

        assertThat(rows).allSatisfy(row -> {
            Application mine = byId.get(row.getApplication().getId());
            Application partner = byId.get(row.getPartnerApplication().getId());
            BigDecimal expected = expectedScore(mine, partner, weights);
            assertThat(row.getScore()).as("점수 불일치: %s↔%s", mine.getId(), partner.getId())
                    .isEqualByComparingTo(expected);
        });
    }

    private void assertAssignPass(List<Match> rows) {
        assertThat(rows).allSatisfy(row -> assertThat(row.getAssignPass()).isBetween(1, 2));
        // 소수 측 한 사람의 첫 파트너만 1차다. 같은 사람의 2·3번째는 2차
        Map<Long, List<Match>> byApplication = rows.stream()
                .collect(Collectors.groupingBy(m -> m.getApplication().getId()));
        assertThat(byApplication.values()).allSatisfy(mine ->
                assertThat(mine.stream().filter(m -> m.getAssignPass() == 1).count()).isLessThanOrEqualTo(1));
    }

    // --- 시드 ----------------------------------------------------------------

    private void seedSettings() {
        appSettingRepository.saveAll(List.of(
                new AppSetting(SettingKey.MATCH_WEIGHT_TAG.key(), "1", null),
                new AppSetting(SettingKey.MATCH_WEIGHT_SLOT.key(), "2", null),
                new AppSetting(SettingKey.MATCH_WEIGHT_AGE_SAME.key(), "1", null),
                new AppSetting(SettingKey.MATCH_WEIGHT_AGE_ADJACENT.key(), "0.5", null),
                new AppSetting(SettingKey.MATCH_MAX_PARTNERS.key(), String.valueOf(MAX_PARTNERS), null)
        ));
    }

    private MatchRound round(int seq, LocalDateTime closeAt, LocalDateTime publishAt) {
        return MatchRound.builder()
                .seq(seq)
                .openAt(FESTIVAL.minusDays(7))
                .closeAt(closeAt)
                .publishAt(publishAt)
                .build();
    }

    /** 무대 1곳과 공연 4개. 절반은 1회차 발표(16:00) 직후, 절반은 2회차 발표(20:00) 이후로 둬 FR-MT-05 복사 규칙을 만든다 */
    private List<TimetableSlot> seedSlots() {
        Place stage = placeRepository.save(Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build());
        List<TimetableSlot> saved = new ArrayList<>();
        List<LocalDateTime> startAts = List.of(
                FESTIVAL.withHour(16).withMinute(15),
                FESTIVAL.withHour(17).withMinute(15),
                FESTIVAL.withHour(20).withMinute(30),
                FESTIVAL.withHour(21).withMinute(30));
        for (int i = 0; i < startAts.size(); i++) {
            saved.add(timetableSlotRepository.save(TimetableSlot.builder()
                    .sortOrder(i + 1).title("공연 " + (i + 1)).slotType(SlotType.CLUB)
                    .startAt(startAts.get(i)).endAt(startAts.get(i).plusMinutes(30)).stage(stage)
                    .build()));
        }
        return saved;
    }

    private List<Application> seedApplications(MatchRound round, int men, int women) {
        List<User> users = new ArrayList<>(men + women);
        for (int i = 0; i < men + women; i++) {
            users.add(User.builder()
                    .provider(OAuthProvider.KAKAO).providerUserId("seed-" + round.getSeq() + "-" + i)
                    .role(UserRole.USER).loginAt(FESTIVAL.minusDays(1))
                    .build());
        }
        users = userRepository.saveAll(users);

        List<Application> applications = new ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            applications.add(application(users.get(i), round, i < men ? Gender.M : Gender.F,
                    "seed" + round.getSeq() + "x" + i, EntryType.NEW, null));
        }
        List<Application> saved = applicationRepository.saveAll(applications);
        applicationRepository.flush();
        return saved;
    }

    private Application application(
            User user,
            MatchRound round,
            Gender gender,
            String instagramId,
            EntryType entryType,
            Application source
    ) {
        Set<MatchTag> tags = new HashSet<>();
        int tagCount = random.nextInt(4); // 0~3개
        while (tags.size() < tagCount) {
            tags.add(MatchTag.values()[random.nextInt(MatchTag.values().length)]);
        }
        AgeBand ageBand = random.nextInt(5) == 0 ? null : AgeBand.values()[random.nextInt(AgeBand.values().length)];
        TimetableSlot wantedSlot = random.nextInt(10) < 4 ? slots.get(random.nextInt(slots.size())) : null;

        return Application.builder()
                .user(user).round(round)
                .instagramId(instagramId).nickname("참가자")
                .gender(gender).ageBand(ageBand).tags(tags).intro(null)
                .wantedSlot(wantedSlot)
                .entryType(entryType).sourceApplication(source)
                .termsVersion("v1").privacyVersion("v1").ageConfirmed(true).agreedAt(FESTIVAL.minusDays(1))
                .build();
    }

    /** 풀에서 남녀를 골라 차단 관계를 만든다. 배치는 이 쌍을 붙이면 안 된다 */
    private List<Block> seedBlocks(List<Application> pool, int count) {
        List<Application> men = pool.stream().filter(a -> a.getGender() == Gender.M).toList();
        List<Application> women = pool.stream().filter(a -> a.getGender() == Gender.F).toList();
        List<Block> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(Block.builder()
                    .reporter(men.get(i).getUser()).target(women.get(i).getUser()).round(round1)
                    .reason(BlockReason.OTHER).detail(null)
                    .build());
        }
        return blockRepository.saveAll(blocks);
    }

    /** 1회차에 매칭됐던 20쌍의 양쪽 회원이 2회차에 다시 신청(REJOIN)한 상태를 만든다 */
    private Set<Set<Long>> rejoinTwentyMatchedPairs() {
        List<Match> round1Rows = matchRepository.findAllByRound_Id(round1.getId()).stream()
                .filter(m -> m.getApplication().getGender() == Gender.F) // 쌍당 한 방향만
                .limit(20)
                .toList();
        Set<Set<Long>> pairs = new HashSet<>();
        List<Application> rejoins = new ArrayList<>();
        int index = 0;
        for (Match row : round1Rows) {
            pairs.add(Set.of(row.getApplication().getUser().getId(), row.getPartnerApplication().getUser().getId()));
            for (Application source : List.of(row.getApplication(), row.getPartnerApplication())) {
                rejoins.add(application(source.getUser(), round2, source.getGender(),
                        "rejoin" + index++, EntryType.REJOIN, source));
            }
        }
        applicationRepository.saveAll(rejoins);
        applicationRepository.flush();
        return pairs;
    }

    // --- 도우미 ---------------------------------------------------------------

    /** "태그 2(음악,카페) + 공연 같음 + 나이 인접" 처럼 점수가 왜 그렇게 나왔는지 사람이 읽을 문장 */
    private static String scoreReason(Application a, Application b, MatchWeights weights) {
        List<String> parts = new ArrayList<>();
        List<String> common = a.getTags().stream().filter(b.getTags()::contains).map(MatchTag::label).sorted().toList();
        parts.add("태그 " + common.size() + (common.isEmpty() ? "" : "(" + String.join(",", common) + ")")
                + " ×" + weights.tag().stripTrailingZeros().toPlainString());
        if (a.getWantedSlot() != null && b.getWantedSlot() != null
                && a.getWantedSlot().getId().equals(b.getWantedSlot().getId())) {
            parts.add("같은 공연 +" + weights.slot().stripTrailingZeros().toPlainString());
        }
        if (a.getAgeBand() != null && b.getAgeBand() != null) {
            if (a.getAgeBand() == b.getAgeBand()) {
                parts.add("나이 동일 +" + weights.ageSame().stripTrailingZeros().toPlainString());
            } else if (a.getAgeBand().isAdjacentTo(b.getAgeBand())) {
                parts.add("나이 인접 +" + weights.ageAdjacent().stripTrailingZeros().toPlainString());
            }
        }
        return String.join(" + ", parts);
    }

    private static String tagNames(Application application) {
        return application.getTags().stream().map(MatchTag::label).sorted().collect(Collectors.joining(","));
    }

    private MatchWeights weights() {
        return MatchWeights.builder()
                .tag(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_TAG))
                .slot(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_SLOT))
                .ageSame(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_SAME))
                .ageAdjacent(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_ADJACENT))
                .build();
    }

    /**
     * 점수를 SRS 규칙대로 다시 계산한다. 엔진의 score()를 부르지 않고 따로 구현해야 "같은 코드를 두 번 부른 것"이 아닌 검증이 된다.
     * 규칙: 공통 태그 수 × tag + 같은 공연 × slot + 나이대 동일 × ageSame 또는 인접 × ageAdjacent
     */
    private static BigDecimal expectedScore(Application a, Application b, MatchWeights weights) {
        long commonTags = a.getTags().stream().filter(b.getTags()::contains).count();
        BigDecimal score = weights.tag().multiply(BigDecimal.valueOf(commonTags));
        if (a.getWantedSlot() != null && b.getWantedSlot() != null
                && a.getWantedSlot().getId().equals(b.getWantedSlot().getId())) {
            score = score.add(weights.slot());
        }
        if (a.getAgeBand() != null && b.getAgeBand() != null) {
            if (a.getAgeBand() == b.getAgeBand()) {
                score = score.add(weights.ageSame());
            } else if (a.getAgeBand().isAdjacentTo(b.getAgeBand())) {
                score = score.add(weights.ageAdjacent());
            }
        }
        return score.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Set<Set<Long>> userPairs(List<Match> rows) {
        return rows.stream()
                .map(row -> Set.of(row.getApplication().getUser().getId(),
                        row.getPartnerApplication().getUser().getId()))
                .collect(Collectors.toSet());
    }

    private static Set<String> pairSignature(List<Match> rows) {
        return rows.stream()
                .map(row -> row.getApplication().getId() + ">" + row.getPartnerApplication().getId()
                        + "@" + row.getScore().stripTrailingZeros().toPlainString())
                .collect(Collectors.toSet());
    }
}
