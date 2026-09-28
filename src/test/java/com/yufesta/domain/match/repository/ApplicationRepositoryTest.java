package com.yufesta.domain.match.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.config.ClockConfig;
import com.yufesta.common.config.JpaConfig;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 홈 요약의 my를 만드는 쿼리를 실제 DB(H2 MySQL 모드)로 검증한다.
 * 서비스 테스트는 이 쿼리를 목으로 대신하므로, 취소·매칭·남의 신청을 가르는 조건은 여기서만 확인된다
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class})
class ApplicationRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 17, 0);

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

    private User me;
    private User other;
    private MatchRound first;
    private MatchRound second;

    @BeforeEach
    void setUp() {
        me = userRepository.save(user("kakao-me"));
        other = userRepository.save(user("kakao-other"));
        first = matchRoundRepository.save(round(1, LocalDateTime.of(2026, 10, 2, 16, 0)));
        second = matchRoundRepository.save(round(2, LocalDateTime.of(2026, 10, 2, 20, 0)));
    }

    @Test
    void 회차별로_내_유효한_신청과_매칭_여부를_한_번에_읽는다() {
        Application myFirst = applicationRepository.save(application(me, first, "me.first", Gender.F));
        Application otherFirst = applicationRepository.save(application(other, first, "other.first", Gender.M));
        applicationRepository.save(application(me, second, "me.second", Gender.F));
        // 방향성 2행 저장
        matchRepository.save(match(first, myFirst, otherFirst));
        matchRepository.save(match(first, otherFirst, myFirst));
        flushAndClear();

        List<MyApplicationState> states = applicationRepository.findMyStates(me.getId(), List.of(second.getId(), first.getId()));

        assertThat(states).containsExactlyInAnyOrder(
                new MyApplicationState(first.getId(), true),
                new MyApplicationState(second.getId(), false)
        );
    }

    @Test
    void 취소한_신청은_나오지_않는다() {
        Application canceled = application(me, first, "me.first", Gender.F);
        canceled.cancel(NOW);
        applicationRepository.save(canceled);
        flushAndClear();

        assertThat(applicationRepository.findMyStates(me.getId(), List.of(first.getId()))).isEmpty();
    }

    @Test
    void 남의_신청과_묻지_않은_회차는_나오지_않는다() {
        applicationRepository.save(application(other, first, "other.first", Gender.M));
        applicationRepository.save(application(me, second, "me.second", Gender.F));
        flushAndClear();

        assertThat(applicationRepository.findMyStates(me.getId(), List.of(first.getId()))).isEmpty();
    }

    @Test
    void 파트너가_여럿이어도_회차당_한_행이다() {
        // 2차 배정으로 한 사람이 여러 명과 매칭될 수 있다. 조인으로 풀면 행이 파트너 수만큼 늘어난다
        User third = userRepository.save(user("kakao-third"));
        Application mine = applicationRepository.save(application(me, first, "me.first", Gender.F));
        Application partnerA = applicationRepository.save(application(other, first, "other.first", Gender.M));
        Application partnerB = applicationRepository.save(application(third, first, "third.first", Gender.M));
        matchRepository.save(match(first, mine, partnerA));
        matchRepository.save(match(first, mine, partnerB));
        flushAndClear();

        assertThat(applicationRepository.findMyStates(me.getId(), List.of(first.getId())))
                .containsExactly(new MyApplicationState(first.getId(), true));
    }

    @Test
    void 쿼리_하나로_끝난다() {
        Application mine = applicationRepository.save(application(me, first, "me.first", Gender.F));
        Application partner = applicationRepository.save(application(other, first, "other.first", Gender.M));
        matchRepository.save(match(first, mine, partner));
        flushAndClear();
        Statistics statistics = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        applicationRepository.findMyStates(me.getId(), List.of(second.getId(), first.getId()));

        // 폴링 한 건마다 도는 쿼리다. 엔티티를 읽어 지연 로딩이 끼어들면 여기서 드러난다
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static User user(String providerUserId) {
        return User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .role(UserRole.USER)
                .loginAt(NOW)
                .build();
    }

    private static MatchRound round(int seq, LocalDateTime publishAt) {
        return MatchRound.builder()
                .seq(seq)
                .openAt(publishAt.minusDays(7))
                .closeAt(publishAt.minusMinutes(10))
                .publishAt(publishAt)
                .build();
    }

    private static Application application(User user, MatchRound round, String instagramId, Gender gender) {
        return Application.builder()
                .user(user)
                .round(round)
                .instagramId(instagramId)
                .nickname("닉네임")
                .gender(gender)
                .ageBand(AgeBand.A22_24)
                .tags(Set.of(MatchTag.MUSIC))
                .intro("안녕하세요")
                .termsVersion("v1")
                .privacyVersion("v1")
                .ageConfirmed(true)
                .agreedAt(NOW)
                .build();
    }

    private static Match match(MatchRound round, Application application, Application partner) {
        return Match.builder()
                .round(round)
                .application(application)
                .partnerApplication(partner)
                .score(new BigDecimal("3.50"))
                .assignPass(1)
                .build();
    }
}
