package com.yufesta.domain.timetable.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.timetable.enums.SlotType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 시각 변경 기록·지연 반영·시계 기준 LIVE 판정을 엔티티 단위로 고정
 */
class TimetableSlotTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 2, 16, 15);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 2, 16, 45);

    @Test
    void 시작_시각이_처음_바뀔_때만_원래_시각을_기록한다() {
        TimetableSlot slot = slot();

        slot.changeTimes(START.plusMinutes(10), END.plusMinutes(10));
        slot.changeTimes(START.plusMinutes(20), END.plusMinutes(20));

        assertThat(slot.getChangedFromStart()).isEqualTo(START);
        assertThat(slot.getStartAt()).isEqualTo(START.plusMinutes(20));
        assertThat(slot.isChanged()).isTrue();
    }

    @Test
    void 종료_시각만_바꾸면_변경으로_기록하지_않는다() {
        TimetableSlot slot = slot();

        slot.changeTimes(START, END.plusMinutes(5));

        assertThat(slot.getChangedFromStart()).isNull();
        assertThat(slot.isChanged()).isFalse();
    }

    @Test
    void 지연은_실제_시각에만_반영되고_원래_시각은_남는다() {
        TimetableSlot slot = slot();

        slot.delay(10);

        assertThat(slot.getEffectiveStartAt()).isEqualTo(START.plusMinutes(10));
        assertThat(slot.getEffectiveEndAt()).isEqualTo(END.plusMinutes(10));
        assertThat(slot.getStartAt()).isEqualTo(START);

        slot.delay(null);
        assertThat(slot.getEffectiveStartAt()).isEqualTo(START);
    }

    @Test
    void 시계_기준_LIVE는_실제_시작_이상_실제_종료_미만이다() {
        TimetableSlot slot = slot();
        slot.delay(10);

        assertThat(slot.isLiveAt(START.plusMinutes(9))).isFalse();
        assertThat(slot.isLiveAt(START.plusMinutes(10))).isTrue();
        assertThat(slot.isLiveAt(END.plusMinutes(9))).isTrue();
        assertThat(slot.isLiveAt(END.plusMinutes(10))).isFalse();
    }

    @Test
    void 수동_지정은_null과_false를_모두_미지정으로_본다() {
        TimetableSlot slot = slot();
        assertThat(slot.isLiveOverride()).isFalse();

        slot.markLive();
        assertThat(slot.isLiveOverride()).isTrue();

        slot.clearLive();
        assertThat(slot.isLiveOverride()).isFalse();
        assertThat(slot.getLiveOverride()).isNull();
    }

    private static TimetableSlot slot() {
        Place stage = Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build();
        return TimetableSlot.builder()
                .sortOrder(4).title("HIPCOM").slotType(SlotType.CLUB)
                .startAt(START).endAt(END).stage(stage)
                .build();
    }
}
