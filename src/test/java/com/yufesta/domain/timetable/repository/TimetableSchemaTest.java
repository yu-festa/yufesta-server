package com.yufesta.domain.timetable.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.config.ClockConfig;
import com.yufesta.common.config.JpaConfig;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 타임테이블 매핑과 정렬·무대 조인 조회를 H2(MySQL 모드)로 고정
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class})
class TimetableSchemaTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 2, 0, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TimetableSlotRepository timetableSlotRepository;

    private Place stage;

    @BeforeEach
    void setUp() {
        stage = em.persist(Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build());
    }

    @Test
    void 순서_그리고_시작_시각순으로_무대와_함께_조회한다() {
        em.persist(slot(2, "천마응원단", DAY.withHour(15).withMinute(55), DAY.withHour(16).withMinute(15)));
        em.persist(slot(1, "신명마당", DAY.withHour(15).withMinute(30), DAY.withHour(15).withMinute(55)));
        em.persist(slot(1, "개회식", DAY.withHour(15), DAY.withHour(15).withMinute(10)));
        em.flush();
        em.clear();

        List<TimetableSlot> slots = timetableSlotRepository.findAllWithStageOrderBySortOrder();

        assertThat(slots).extracting(TimetableSlot::getTitle).containsExactly("개회식", "신명마당", "천마응원단");
        // join fetch라 영속성 컨텍스트를 비운 뒤에도 무대 이름을 추가 조회 없이 읽는다
        assertThat(slots.get(0).getStage().getName()).isEqualTo("중앙 무대");
    }

    @Test
    void 선택_컬럼은_비워_저장할_수_있고_수동_LIVE만_따로_찾는다() {
        TimetableSlot plain = em.persist(slot(1, "개회식", DAY.withHour(15), DAY.withHour(15).withMinute(10)));
        TimetableSlot live = em.persist(slot(2, "신명마당", DAY.withHour(15).withMinute(30), DAY.withHour(15).withMinute(55)));
        live.markLive();
        em.flush();
        em.clear();

        TimetableSlot found = timetableSlotRepository.findById(plain.getId()).orElseThrow();
        assertThat(found.getClub()).isNull();
        assertThat(found.getDelayMinutes()).isNull();
        assertThat(found.getLiveOverride()).isNull();
        assertThat(found.getChangedFromStart()).isNull();
        assertThat(timetableSlotRepository.findAllByLiveOverrideTrue())
                .extracting(TimetableSlot::getId)
                .containsExactly(live.getId());
    }

    @Test
    void 동아리가_연결된_공연만_동아리와_함께_조회하고_동아리별로도_찾는다() {
        Club hipcom = em.persist(Club.builder().name("HIPCOM").intro("힙합").sortOrder(1).build());
        em.persist(slot(1, "개회식", DAY.withHour(15), DAY.withHour(15).withMinute(10)));
        TimetableSlot show = slot(2, "HIPCOM", DAY.withHour(16).withMinute(15), DAY.withHour(16).withMinute(45));
        show.update(show.getTitle(), show.getSlotType(), stage, hipcom);
        em.persist(show);
        em.flush();
        em.clear();

        List<TimetableSlot> withClub = timetableSlotRepository.findAllWithClubOrderBySortOrder();
        assertThat(withClub).extracting(TimetableSlot::getTitle).containsExactly("HIPCOM");
        assertThat(withClub.get(0).getClub().getName()).isEqualTo("HIPCOM");
        assertThat(timetableSlotRepository.findAllByClub_Id(hipcom.getId())).hasSize(1);
        // 전체 목록은 동아리 없는 공연도 포함한다(left join)
        assertThat(timetableSlotRepository.findAllWithStageOrderBySortOrder()).hasSize(2);
    }

    private TimetableSlot slot(int sortOrder, String title, LocalDateTime startAt, LocalDateTime endAt) {
        return TimetableSlot.builder()
                .sortOrder(sortOrder).title(title).slotType(SlotType.CLUB)
                .startAt(startAt).endAt(endAt).stage(stage)
                .build();
    }
}
