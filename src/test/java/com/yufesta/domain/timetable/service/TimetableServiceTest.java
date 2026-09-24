package com.yufesta.domain.timetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.timetable.dto.response.TimetableResponse;
import com.yufesta.domain.timetable.dto.response.TimetableSlotResponse;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.repository.TimetableSlotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TimetableServiceTest {

    // 16:20. 시간표상 HIPCOM(16:15~16:45)이 진행 중인 시각
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 16, 20);

    @Mock
    private TimetableSlotRepository timetableSlotRepository;

    private TimetableService timetableService;

    private TimetableSlot cosmos;
    private TimetableSlot hipcom;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        timetableService = new TimetableService(timetableSlotRepository, clock);
        hipcom = slot(4L, "HIPCOM", NOW.withHour(16).withMinute(15), NOW.withHour(16).withMinute(45));
        cosmos = slot(5L, "COSMOS", NOW.withHour(16).withMinute(45), NOW.withHour(17).withMinute(15));
        when(timetableSlotRepository.findAllWithStageOrderBySortOrder()).thenReturn(List.of(hipcom, cosmos));
    }

    @Test
    void 수동_지정이_없으면_시계_기준으로_진행_중을_판정한다() {
        TimetableResponse response = timetableService.getTimetable();

        assertThat(response.serverNow()).isEqualTo(NOW);
        assertThat(response.slots()).extracting(TimetableSlotResponse::title, TimetableSlotResponse::isLive)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("HIPCOM", true),
                        org.assertj.core.groups.Tuple.tuple("COSMOS", false)
                );
    }

    @Test
    void 수동_지정이_있으면_시계와_무관하게_그_항목만_진행_중이다() {
        cosmos.markLive();

        TimetableResponse response = timetableService.getTimetable();

        assertThat(response.slots()).extracting(TimetableSlotResponse::title, TimetableSlotResponse::isLive)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("HIPCOM", false),
                        org.assertj.core.groups.Tuple.tuple("COSMOS", true)
                );
    }

    @Test
    void 지연이_있으면_실제_시각으로_판정하고_원래_시각도_함께_준다() {
        // 10분 지연이면 HIPCOM은 16:25에 시작하므로 16:20에는 아직 진행 전
        hipcom.delay(10);

        TimetableSlotResponse first = timetableService.getTimetable().slots().get(0);

        assertThat(first.isLive()).isFalse();
        assertThat(first.startAt()).isEqualTo(NOW.withHour(16).withMinute(15));
        assertThat(first.effectiveStartAt()).isEqualTo(NOW.withHour(16).withMinute(25));
        assertThat(first.delayMinutes()).isEqualTo(10);
        assertThat(first.stage().name()).isEqualTo("중앙 무대");
    }

    @Test
    void 시각을_바꾼_항목은_변경_표시와_원래_시각을_준다() {
        hipcom.changeTimes(NOW.withHour(16).withMinute(30), NOW.withHour(17));

        TimetableSlotResponse first = timetableService.getTimetable().slots().get(0);

        assertThat(first.isChanged()).isTrue();
        assertThat(first.changedFromStart()).isEqualTo(NOW.withHour(16).withMinute(15));
        assertThat(first.isLive()).isFalse();
    }

    private static TimetableSlot slot(Long id, String title, LocalDateTime startAt, LocalDateTime endAt) {
        Place stage = Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build();
        ReflectionTestUtils.setField(stage, "id", 1L);
        TimetableSlot slot = TimetableSlot.builder()
                .sortOrder(id.intValue()).title(title).slotType(SlotType.CLUB)
                .startAt(startAt).endAt(endAt).stage(stage)
                .build();
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }
}
