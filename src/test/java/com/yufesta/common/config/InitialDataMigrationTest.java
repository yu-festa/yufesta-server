package com.yufesta.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.club.repository.ClubRepository;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.repository.PlaceRepository;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.repository.TimetableSlotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

/**
 * 초기 데이터 마이그레이션(V2·V3·V4)이 문법 오류 없이 실행되고 기대한 행을 넣는지 H2(MySQL 모드)로 확인.
 * V1은 MySQL 전용 문법이라 여기서 실행하지 않고, 스키마는 Hibernate가 엔티티로 만든다. 엔티티 저장이 필요한 케이스를 위해 Auditing 설정을 넣는다
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class})
class InitialDataMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private MatchRoundRepository matchRoundRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TimetableSlotRepository timetableSlotRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Test
    void 초기_데이터는_설정_16행과_회차_2행을_넣는다() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__initial_data.sql")).execute(dataSource);

        assertThat(appSettingRepository.count()).isEqualTo(16);
        assertThat(appSettingRepository.findById("admin.allowlist"))
                .get()
                .extracting(setting -> setting.getSettingValue())
                .isEqualTo("");
        assertThat(appSettingRepository.findById("match.max_partners"))
                .get()
                .extracting(setting -> setting.getSettingValue())
                .isEqualTo("3");
        assertThat(matchRoundRepository.findAllByOrderBySeqAsc())
                .extracting(round -> round.getSeq(), round -> round.getStatus())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, RoundStatus.SCHEDULED),
                        org.assertj.core.groups.Tuple.tuple(2, RoundStatus.SCHEDULED)
                );
    }

    @Test
    void 타임테이블_초기_데이터는_무대_1행과_공연_13행을_순서대로_넣는다() {
        populate("db/migration/V3__timetable_initial.sql");

        assertThat(placeRepository.findAllByCategoryAndActiveTrueOrderBySortOrderAscNameAsc(PlaceCategory.STAGE))
                .extracting(Place::getName)
                .containsExactly("중앙 무대");
        List<TimetableSlot> slots = timetableSlotRepository.findAllWithStageOrderBySortOrder();
        assertThat(slots).hasSize(13);
        assertThat(slots.get(0))
                .extracting(TimetableSlot::getTitle, TimetableSlot::getSlotType, TimetableSlot::getStartAt)
                .containsExactly("개회식", SlotType.EVENT, LocalDateTime.of(2026, 10, 2, 15, 0));
        assertThat(slots.get(12).getSlotType()).isEqualTo(SlotType.GUEST);
        assertThat(slots).filteredOn(slot -> slot.getSlotType() == SlotType.CLUB).hasSize(9);
        assertThat(slots).allSatisfy(slot -> assertThat(slot.getStage().getName()).isEqualTo("중앙 무대"));
    }

    @Test
    void STAGE_장소가_이미_있으면_무대를_새로_만들지_않고_그_장소에_공연을_연결한다() {
        Place existing = placeRepository.saveAndFlush(Place.builder()
                .name("야외 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8300000")).longitude(new BigDecimal("128.7500000"))
                .sortOrder(0).active(true)
                .build());

        populate("db/migration/V3__timetable_initial.sql");

        assertThat(placeRepository.count()).isEqualTo(1);
        assertThat(timetableSlotRepository.findAllWithStageOrderBySortOrder())
                .allSatisfy(slot -> assertThat(slot.getStage().getId()).isEqualTo(existing.getId()));
    }

    @Test
    void 라인업_초기_데이터는_동아리_9행을_넣고_CLUB_공연_9건에_연결한다() {
        populate("db/migration/V3__timetable_initial.sql");
        populate("db/migration/V4__club_initial.sql");

        assertThat(clubRepository.findAllByOrderBySortOrderAscNameAsc())
                .extracting(Club::getName)
                .containsExactly("신명마당", "천마응원단", "HIPCOM", "코스모스", "The WE", "ECHOES", "예사가락", "BLUEWAVE", "MAX & ZENITH");
        List<TimetableSlot> linked = timetableSlotRepository.findAllWithClubOrderBySortOrder();
        assertThat(linked).hasSize(9);
        assertThat(linked).allSatisfy(slot -> assertThat(slot.getSlotType()).isEqualTo(SlotType.CLUB));
        // 공연명과 동아리명 표기가 다른 항목도 연결된다
        assertThat(linked).filteredOn(slot -> slot.getTitle().equals("COSMOS"))
                .singleElement().extracting(slot -> slot.getClub().getName()).isEqualTo("코스모스");
        assertThat(linked).filteredOn(slot -> slot.getTitle().equals("BLUE WAVE"))
                .singleElement().extracting(slot -> slot.getClub().getName()).isEqualTo("BLUEWAVE");
        assertThat(timetableSlotRepository.findAllWithStageOrderBySortOrder())
                .filteredOn(slot -> slot.getSlotType() != SlotType.CLUB)
                .allSatisfy(slot -> assertThat(slot.getClub()).isNull());
    }

    private void populate(String path) {
        new ResourceDatabasePopulator(new ClassPathResource(path)).execute(dataSource);
    }
}
