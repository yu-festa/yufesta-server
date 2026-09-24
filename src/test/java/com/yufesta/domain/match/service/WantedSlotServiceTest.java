package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.response.MatchSlotResponse;
import com.yufesta.domain.match.dto.response.MatchSlotsResponse;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.service.TimetableService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WantedSlotServiceTest {

    private static final LocalDateTime PUBLISH_AT = LocalDateTime.of(2026, 10, 2, 16, 0);

    @Mock
    private MatchRoundService matchRoundService;

    @Mock
    private TimetableService timetableService;

    @InjectMocks
    private WantedSlotService wantedSlotService;

    private final MatchRound round = MatchRound.builder()
            .seq(1).openAt(PUBLISH_AT.minusDays(7)).closeAt(PUBLISH_AT.minusMinutes(10)).publishAt(PUBLISH_AT)
            .build();

    @Test
    void 선택지는_현재_회차_발표_시각을_기준으로_타임테이블에_묻는다() {
        when(matchRoundService.getCurrentRound()).thenReturn(round);
        when(timetableService.getSlotsStartingAtOrAfter(PUBLISH_AT)).thenReturn(List.of(slot(4L, PUBLISH_AT.plusMinutes(15))));

        MatchSlotsResponse response = wantedSlotService.getSelectableSlots();

        assertThat(response.roundSeq()).isEqualTo(1);
        assertThat(response.publishAt()).isEqualTo(PUBLISH_AT);
        assertThat(response.slots()).extracting(MatchSlotResponse::id).containsExactly(4L);
        assertThat(response.slots().get(0).stageName()).isEqualTo("중앙 무대");
    }

    @Test
    void 선택_없음은_null을_그대로_돌려준다() {
        assertThat(wantedSlotService.requireSelectable(round, null)).isNull();
    }

    @Test
    void 발표_시각_이후_시작_공연은_통과하고_그_전_공연은_APPLICATION_SLOT_NOT_SELECTABLE다() {
        TimetableSlot afterPublish = slot(4L, PUBLISH_AT);
        TimetableSlot beforePublish = slot(3L, PUBLISH_AT.minusMinutes(5));
        when(timetableService.getSlot(4L)).thenReturn(afterPublish);
        when(timetableService.getSlot(3L)).thenReturn(beforePublish);

        assertThat(wantedSlotService.requireSelectable(round, 4L)).isSameAs(afterPublish);
        assertThatThrownBy(() -> wantedSlotService.requireSelectable(round, 3L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.APPLICATION_SLOT_NOT_SELECTABLE);
    }

    @Test
    void 없는_공연은_타임테이블의_TIMETABLE_SLOT_NOT_FOUND가_그대로_난다() {
        when(timetableService.getSlot(99L)).thenThrow(new CustomException(ErrorCode.TIMETABLE_SLOT_NOT_FOUND));

        assertThatThrownBy(() -> wantedSlotService.requireSelectable(round, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_SLOT_NOT_FOUND);
    }

    private static TimetableSlot slot(Long id, LocalDateTime startAt) {
        Place stage = Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build();
        ReflectionTestUtils.setField(stage, "id", 1L);
        TimetableSlot slot = TimetableSlot.builder()
                .sortOrder(id.intValue()).title("공연 " + id).slotType(SlotType.CLUB)
                .startAt(startAt).endAt(startAt.plusMinutes(30)).stage(stage)
                .build();
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }
}
