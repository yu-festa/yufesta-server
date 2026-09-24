package com.yufesta.domain.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.club.dto.response.ClubPerformanceResponse;
import com.yufesta.domain.club.dto.response.ClubResponse;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.club.repository.ClubRepository;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.enums.SlotType;
import com.yufesta.domain.timetable.service.TimetableService;
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
class ClubServiceTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 2, 0, 0);

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private TimetableService timetableService;

    @InjectMocks
    private ClubService clubService;

    @Test
    void 목록은_표시_순서대로_주고_카드마다_그_동아리의_공연만_붙인다() {
        Club hipcom = club(3L, "HIPCOM");
        Club cosmos = club(4L, "코스모스");
        Club noShow = club(5L, "공연 없는 동아리");
        when(clubRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(hipcom, cosmos, noShow));
        when(timetableService.getSlotsWithClub()).thenReturn(List.of(
                slot(4L, "HIPCOM", 16, 15, hipcom),
                slot(5L, "COSMOS", 16, 45, cosmos),
                slot(14L, "HIPCOM 앵콜", 21, 0, hipcom)
        ));

        List<ClubResponse> result = clubService.getClubs();

        assertThat(result).extracting(ClubResponse::name).containsExactly("HIPCOM", "코스모스", "공연 없는 동아리");
        assertThat(result.get(0).performances()).extracting(ClubPerformanceResponse::slotId).containsExactly(4L, 14L);
        assertThat(result.get(0).performances().get(0).stageName()).isEqualTo("중앙 무대");
        assertThat(result.get(1).performances()).extracting(ClubPerformanceResponse::title).containsExactly("COSMOS");
        assertThat(result.get(2).performances()).isEmpty();
    }

    @Test
    void 카드_하나는_그_동아리_공연만_붙여_준다() {
        Club hipcom = club(3L, "HIPCOM");
        when(clubRepository.findById(3L)).thenReturn(Optional.of(hipcom));
        TimetableSlot show = slot(4L, "HIPCOM", 16, 15, hipcom);
        show.delay(10);
        when(timetableService.getSlotsWithClub()).thenReturn(List.of(show, slot(5L, "COSMOS", 16, 45, club(4L, "코스모스"))));

        ClubResponse result = clubService.getClub(3L);

        assertThat(result.instagramUrl()).isEqualTo("https://www.instagram.com/hipcom_yu");
        assertThat(result.performances()).hasSize(1);
        assertThat(result.performances().get(0).effectiveStartAt()).isEqualTo(DAY.withHour(16).withMinute(25));
    }

    @Test
    void 없는_동아리면_CLUB_NOT_FOUND를_던진다() {
        when(clubRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubService.getClub(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CLUB_NOT_FOUND);
        assertThatThrownBy(() -> clubService.getClubEntity(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CLUB_NOT_FOUND);
    }

    private static Club club(Long id, String name) {
        Club club = Club.builder()
                .name(name).intro("소개").genre("힙합").signatureSong("최준현-거북당")
                .instagramUrl("https://www.instagram.com/hipcom_yu").sortOrder(id.intValue())
                .build();
        ReflectionTestUtils.setField(club, "id", id);
        return club;
    }

    private static TimetableSlot slot(Long id, String title, int hour, int minute, Club club) {
        Place stage = Place.builder()
                .name("중앙 무대").category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210")).longitude(new BigDecimal("128.7542100"))
                .sortOrder(0).active(true)
                .build();
        ReflectionTestUtils.setField(stage, "id", 1L);
        TimetableSlot slot = TimetableSlot.builder()
                .sortOrder(id.intValue()).title(title).slotType(SlotType.CLUB)
                .startAt(DAY.withHour(hour).withMinute(minute)).endAt(DAY.withHour(hour).withMinute(minute).plusMinutes(30))
                .stage(stage).club(club)
                .build();
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }
}
