package com.yufesta.domain.timetable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.service.PlaceService;
import com.yufesta.domain.timetable.dto.request.ChangeSlotTimesRequest;
import com.yufesta.domain.timetable.dto.request.CreateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.request.DelaySlotRequest;
import com.yufesta.domain.timetable.dto.request.LiveSlotRequest;
import com.yufesta.domain.timetable.dto.request.ReorderSlotsRequest;
import com.yufesta.domain.timetable.dto.response.AdminTimetableSlotResponse;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.repository.TimetableSlotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TimetableAdminServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 2, 16, 15);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 2, 16, 45);

    @Mock
    private TimetableSlotRepository timetableSlotRepository;

    @Mock
    private PlaceService placeService;

    @InjectMocks
    private TimetableAdminService timetableAdminService;

    @Test
    void 등록_시_무대가_STAGE_장소가_아니면_TIMETABLE_STAGE_INVALID를_던진다() {
        when(placeService.getPlaceEntity(9L)).thenReturn(place(9L, PlaceCategory.TOILET));

        assertThatThrownBy(() -> timetableAdminService.create(createRequest(9L, START, END)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_STAGE_INVALID);
        verify(timetableSlotRepository, never()).save(any());
    }

    @Test
    void 종료가_시작보다_뒤가_아니면_TIMETABLE_INVALID_TIME을_던진다() {
        assertThatThrownBy(() -> timetableAdminService.create(createRequest(1L, START, START)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_INVALID_TIME);
        assertThatThrownBy(() -> timetableAdminService.changeTimes(4L, new ChangeSlotTimesRequest(END, START)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_INVALID_TIME);
    }

    @Test
    void 등록하면_무대와_함께_저장하고_응답에_무대_이름을_넣는다() {
        Place stage = place(1L, PlaceCategory.STAGE);
        when(placeService.getPlaceEntity(1L)).thenReturn(stage);
        when(timetableSlotRepository.save(any(TimetableSlot.class))).thenAnswer(invocation -> {
            TimetableSlot saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 4L);
            return saved;
        });

        AdminTimetableSlotResponse response = timetableAdminService.create(createRequest(1L, START, END));

        assertThat(response.id()).isEqualTo(4L);
        assertThat(response.stageName()).isEqualTo("중앙 무대");
        assertThat(response.liveOverride()).isFalse();
    }

    @Test
    void 시각_변경은_최초_1회만_원래_시각을_기록한다() {
        TimetableSlot slot = slot(4L, 4);
        when(timetableSlotRepository.findById(4L)).thenReturn(Optional.of(slot));

        timetableAdminService.changeTimes(4L, new ChangeSlotTimesRequest(START.plusMinutes(10), END.plusMinutes(10)));
        AdminTimetableSlotResponse second = timetableAdminService.changeTimes(
                4L, new ChangeSlotTimesRequest(START.plusMinutes(20), END.plusMinutes(20)));

        assertThat(second.changedFromStart()).isEqualTo(START);
        assertThat(second.startAt()).isEqualTo(START.plusMinutes(20));
    }

    @Test
    void 진행_중_지정은_다른_공연의_지정을_모두_풀고_이_공연만_남긴다() {
        TimetableSlot previous = slot(3L, 3);
        previous.markLive();
        TimetableSlot target = slot(4L, 4);
        when(timetableSlotRepository.findById(4L)).thenReturn(Optional.of(target));
        when(timetableSlotRepository.findAllByLiveOverrideTrue()).thenReturn(List.of(previous));

        AdminTimetableSlotResponse response = timetableAdminService.setLive(4L, new LiveSlotRequest(true));

        assertThat(response.liveOverride()).isTrue();
        assertThat(previous.isLiveOverride()).isFalse();
    }

    @Test
    void 진행_중_해제는_이_공연만_풀고_다른_공연은_건드리지_않는다() {
        TimetableSlot target = slot(4L, 4);
        target.markLive();
        when(timetableSlotRepository.findById(4L)).thenReturn(Optional.of(target));

        AdminTimetableSlotResponse response = timetableAdminService.setLive(4L, new LiveSlotRequest(false));

        assertThat(response.liveOverride()).isFalse();
        verify(timetableSlotRepository, never()).findAllByLiveOverrideTrue();
    }

    @Test
    void 지연은_넣고_null로_해제한다() {
        TimetableSlot slot = slot(4L, 4);
        when(timetableSlotRepository.findById(4L)).thenReturn(Optional.of(slot));

        assertThat(timetableAdminService.setDelay(4L, new DelaySlotRequest(10)).delayMinutes()).isEqualTo(10);
        assertThat(timetableAdminService.setDelay(4L, new DelaySlotRequest(null)).delayMinutes()).isNull();
    }

    @Test
    void 순서_변경은_나열_순서대로_1부터_다시_매긴다() {
        TimetableSlot a = slot(1L, 1);
        TimetableSlot b = slot(2L, 2);
        TimetableSlot c = slot(3L, 3);
        when(timetableSlotRepository.findAllWithStageOrderBySortOrder()).thenReturn(List.of(a, b, c));

        List<AdminTimetableSlotResponse> result = timetableAdminService.reorder(new ReorderSlotsRequest(List.of(3L, 1L, 2L)));

        assertThat(result).extracting(AdminTimetableSlotResponse::id).containsExactly(3L, 1L, 2L);
        assertThat(c.getSortOrder()).isEqualTo(1);
        assertThat(a.getSortOrder()).isEqualTo(2);
        assertThat(b.getSortOrder()).isEqualTo(3);
    }

    @Test
    void 순서_목록에_공연이_빠지거나_중복되면_TIMETABLE_ORDER_INVALID를_던진다() {
        when(timetableSlotRepository.findAllWithStageOrderBySortOrder()).thenReturn(List.of(slot(1L, 1), slot(2L, 2)));

        assertThatThrownBy(() -> timetableAdminService.reorder(new ReorderSlotsRequest(List.of(1L))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_ORDER_INVALID);
        assertThatThrownBy(() -> timetableAdminService.reorder(new ReorderSlotsRequest(List.of(1L, 1L))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_ORDER_INVALID);
    }

    @Test
    void 없는_공연이면_TIMETABLE_SLOT_NOT_FOUND를_던진다() {
        when(timetableSlotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timetableAdminService.delete(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TIMETABLE_SLOT_NOT_FOUND);
    }

    @Test
    void 삭제하면_리포지토리에서_지운다() {
        TimetableSlot slot = slot(4L, 4);
        when(timetableSlotRepository.findById(4L)).thenReturn(Optional.of(slot));

        timetableAdminService.delete(4L);

        verify(timetableSlotRepository).delete(slot);
    }

    private static CreateTimetableSlotRequest createRequest(Long stagePlaceId, LocalDateTime startAt, LocalDateTime endAt) {
        return CreateTimetableSlotRequest.builder()
                .title("HIPCOM").slotType(SlotType.CLUB)
                .startAt(startAt).endAt(endAt)
                .stagePlaceId(stagePlaceId).sortOrder(4)
                .build();
    }

    private static Place place(Long id, PlaceCategory category) {
        Place place = Place.builder()
                .name("중앙 무대").category(category)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private static TimetableSlot slot(Long id, int sortOrder) {
        TimetableSlot slot = TimetableSlot.builder()
                .sortOrder(sortOrder).title("공연 " + id).slotType(SlotType.CLUB)
                .startAt(START).endAt(END).stage(place(1L, PlaceCategory.STAGE))
                .build();
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }
}
