package com.yufesta.domain.club.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.config.ClockConfig;
import com.yufesta.common.config.JpaConfig;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 라인업 매핑과 정렬 조회를 H2(MySQL 모드)로 고정
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class})
class ClubSchemaTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClubRepository clubRepository;

    @Test
    void 표시_순서_그리고_이름순으로_조회하고_선택_컬럼은_비워_저장할_수_있다() {
        User staff = em.persist(User.builder()
                .provider(OAuthProvider.KAKAO).providerUserId("kakao-staff").role(UserRole.STAFF)
                .loginAt(LocalDateTime.of(2026, 9, 24, 12, 0))
                .build());
        em.persist(club("예사가락", 2, staff));
        em.persist(club("COSMOS", 1, null));
        em.persist(club("BLUEWAVE", 1, null));
        em.flush();
        em.clear();

        assertThat(clubRepository.findAllByOrderBySortOrderAscNameAsc())
                .extracting(Club::getName)
                .containsExactly("BLUEWAVE", "COSMOS", "예사가락");
        Club found = clubRepository.findAllByOrderBySortOrderAscNameAsc().get(2);
        assertThat(found.getCreatedBy().getId()).isEqualTo(staff.getId());
        assertThat(found.getGenre()).isNull();
        assertThat(found.getPhotoUrl()).isNull();
    }

    private static Club club(String name, int sortOrder, User createdBy) {
        return Club.builder()
                .name(name).intro("소개").sortOrder(sortOrder).createdBy(createdBy)
                .build();
    }
}
