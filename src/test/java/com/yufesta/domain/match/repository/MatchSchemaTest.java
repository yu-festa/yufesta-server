package com.yufesta.domain.match.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.config.ClockConfig;
import com.yufesta.common.config.JpaConfig;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.BlockDecision;
import com.yufesta.domain.match.enums.BlockReason;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

/**
 * 인스타팅 테이블 매핑과 DB 제약을 H2(MySQL 모드)로 고정. 유니크 제약은 신청·신고의 동시성 방어선이다
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class})
class MatchSchemaTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRoundRepository matchRoundRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private BlockRepository blockRepository;

    private User alice;
    private User bob;
    private MatchRound round;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(user("kakao-alice"));
        bob = userRepository.save(user("kakao-bob"));
        round = matchRoundRepository.save(MatchRound.builder()
                .seq(1)
                .openAt(NOW.minusDays(4))
                .closeAt(NOW.plusDays(3))
                .publishAt(NOW.plusDays(3).plusMinutes(10))
                .build());
    }

    @Test
    void 같은_회원이_같은_회차에_두_번_신청하면_uk_app_user_round_위반이다() {
        applicationRepository.saveAndFlush(application(alice, "alice_insta"));

        assertThatThrownBy(() -> applicationRepository.saveAndFlush(application(alice, "other_insta")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_회차에_같은_인스타_ID면_uk_app_round_insta_위반이다() {
        applicationRepository.saveAndFlush(application(alice, "same_insta"));

        assertThatThrownBy(() -> applicationRepository.saveAndFlush(application(bob, "same_insta")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_회차의_같은_쌍을_두_번_저장하면_uk_match_pair_위반이다() {
        Application a = applicationRepository.save(application(alice, "alice_insta"));
        Application b = applicationRepository.save(application(bob, "bob_insta"));
        matchRepository.saveAndFlush(match(a, b));

        assertThatThrownBy(() -> matchRepository.saveAndFlush(match(a, b)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_신고자가_같은_대상을_두_번_신고하면_uk_blocks_pair_위반이다() {
        blockRepository.saveAndFlush(block(alice, bob));

        assertThatThrownBy(() -> blockRepository.saveAndFlush(block(alice, bob)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 태그는_application_tags에_enum_이름으로_저장되고_신청_삭제_시_함께_삭제된다() {
        Application saved = applicationRepository.saveAndFlush(application(alice, "alice_insta"));
        em.clear();

        Application loaded = applicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getTags()).containsExactlyInAnyOrder(MatchTag.MUSIC, MatchTag.CAFE, MatchTag.PET);
        assertThat(countTagRows(saved.getId())).isEqualTo(3);
        assertThat(em.getEntityManager()
                .createNativeQuery("select tag from application_tags where application_id = ?1 order by tag")
                .setParameter(1, saved.getId())
                .getResultList()).containsExactly("CAFE", "MUSIC", "PET");

        applicationRepository.delete(loaded);
        applicationRepository.flush();

        assertThat(countTagRows(saved.getId())).isZero();
    }

    @Test
    void 나이대는_ERD_값으로_저장되고_enum으로_복원된다() {
        Application saved = applicationRepository.saveAndFlush(application(alice, "alice_insta"));
        em.clear();

        Object stored = em.getEntityManager()
                .createNativeQuery("select age_band from applications where id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();
        assertThat(stored).isEqualTo("22-24");
        assertThat(applicationRepository.findById(saved.getId()).orElseThrow().getAgeBand()).isEqualTo(AgeBand.A22_24);
    }

    @Test
    void 배치_풀은_취소_신청과_매칭_차단_회원을_제외하고_태그를_함께_읽는다() {
        User blocked = userRepository.save(user("kakao-blocked"));
        em.getEntityManager()
                .createQuery("update User u set u.matchingBlockedAt = :now where u.id = :id")
                .setParameter("now", NOW)
                .setParameter("id", blocked.getId())
                .executeUpdate();
        applicationRepository.save(application(alice, "alice_insta"));
        Application canceled = applicationRepository.save(application(bob, "bob_insta"));
        canceled.cancel(NOW);
        applicationRepository.save(application(blocked, "blocked_insta"));
        applicationRepository.flush();
        em.clear();

        assertThat(applicationRepository.findPoolByRoundId(round.getId()))
                .extracting(Application::getInstagramId)
                .containsExactly("alice_insta");
        assertThat(applicationRepository.countByRound_IdAndCanceledAtIsNull(round.getId())).isEqualTo(2);
    }

    @Test
    void 유효_신고_수는_기각된_건을_제외하고_미검토_건을_포함한다() {
        User carol = userRepository.save(user("kakao-carol"));
        blockRepository.save(block(alice, bob));
        Block dismissed = blockRepository.save(block(carol, bob));
        dismissed.review(BlockDecision.DISMISS, NOW);
        blockRepository.flush();

        assertThat(blockRepository.countValidReports(bob.getId(), BlockDecision.DISMISS)).isEqualTo(1);
    }

    @Test
    void 매칭_회원_쌍_조회와_매칭된_신청_id_조회와_회차_결과_삭제가_동작한다() {
        MatchRound round2 = matchRoundRepository.save(MatchRound.builder()
                .seq(2).openAt(NOW).closeAt(NOW.plusDays(3).plusHours(4)).publishAt(NOW.plusDays(3).plusHours(4).plusMinutes(10)).build());
        Application a = applicationRepository.save(application(alice, "alice_insta"));
        Application b = applicationRepository.save(application(bob, "bob_insta"));
        matchRepository.save(match(a, b));
        matchRepository.save(match(b, a));
        matchRepository.flush();
        em.clear();

        assertThat(matchRepository.findMatchedUserIdPairsBeforeSeq(2))
                .extracting(pair -> pair.userId(), pair -> pair.otherUserId())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(alice.getId(), bob.getId()),
                        org.assertj.core.groups.Tuple.tuple(bob.getId(), alice.getId()));
        assertThat(matchRepository.findMatchedUserIdPairsBeforeSeq(1)).isEmpty();
        assertThat(matchRepository.findMatchedApplicationIdsByRoundId(round.getId())).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(matchRepository.findMatchedApplicationIdsByRoundId(round2.getId())).isEmpty();

        assertThat(matchRepository.deleteAllByRoundId(round.getId())).isEqualTo(2);
        assertThat(matchRepository.countByRound_Id(round.getId())).isZero();
        assertThat(blockRepository.findAllUserIdPairs()).isEmpty();
    }

    @Test
    void 보고_싶은_공연을_저장하고_공연_삭제_전_일괄_해제로_비운다() {
        Place stage = em.persist(Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build());
        TimetableSlot slot = em.persist(TimetableSlot.builder()
                .sortOrder(4).title("HIPCOM").slotType(SlotType.CLUB)
                .startAt(NOW.plusDays(3).plusHours(1)).endAt(NOW.plusDays(3).plusHours(2)).stage(stage)
                .build());
        Application application = application(alice, "alice");
        application.changeWantedSlot(slot);
        Long applicationId = applicationRepository.save(application).getId();
        em.flush();
        em.clear();

        assertThat(applicationRepository.findById(applicationId).orElseThrow().getWantedSlot().getTitle()).isEqualTo("HIPCOM");

        int detached = applicationRepository.detachWantedSlot(slot.getId());

        assertThat(detached).isEqualTo(1);
        assertThat(applicationRepository.findById(applicationId).orElseThrow().getWantedSlot()).isNull();
    }

    private long countTagRows(Long applicationId) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("select count(*) from application_tags where application_id = ?1")
                .setParameter(1, applicationId)
                .getSingleResult()).longValue();
    }

    private static User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .role(UserRole.USER)
                .loginAt(NOW)
                .build();
    }

    private Application application(User user, String instagramId) {
        return Application.builder()
                .user(user)
                .round(round)
                .instagramId(instagramId)
                .nickname("닉네임")
                .gender(user == bob ? Gender.M : Gender.F)
                .ageBand(AgeBand.A22_24)
                .tags(Set.of(MatchTag.MUSIC, MatchTag.CAFE, MatchTag.PET))
                .intro("안녕하세요")
                .termsVersion("v1")
                .privacyVersion("v1")
                .ageConfirmed(true)
                .agreedAt(NOW)
                .build();
    }

    private Match match(Application application, Application partner) {
        return Match.builder()
                .round(round)
                .application(application)
                .partnerApplication(partner)
                .score(new BigDecimal("3.50"))
                .assignPass(1)
                .build();
    }

    private Block block(User reporter, User target) {
        return Block.builder()
                .reporter(reporter)
                .target(target)
                .round(round)
                .reason(BlockReason.FAKE)
                .detail("상세")
                .build();
    }
}
