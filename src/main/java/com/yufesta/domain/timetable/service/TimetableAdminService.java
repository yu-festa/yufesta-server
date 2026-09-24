package com.yufesta.domain.timetable.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.club.service.ClubService;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.service.PlaceService;
import com.yufesta.domain.timetable.dto.request.ChangeSlotTimesRequest;
import com.yufesta.domain.timetable.dto.request.CreateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.request.DelaySlotRequest;
import com.yufesta.domain.timetable.dto.request.LiveSlotRequest;
import com.yufesta.domain.timetable.dto.request.ReorderSlotsRequest;
import com.yufesta.domain.timetable.dto.request.UpdateTimetableSlotRequest;
import com.yufesta.domain.timetable.dto.response.AdminTimetableSlotResponse;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.repository.TimetableSlotRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 타임테이블 관리(FR-ADM-06). 등록·수정·삭제와 현장 대응(시각 변경·지연·LIVE 지정·순서 변경)을 처리한다
 */
@Service
@Transactional(readOnly = true)
public class TimetableAdminService {

    private final TimetableSlotRepository timetableSlotRepository;
    private final PlaceService placeService;
    private final ClubService clubService;

    public TimetableAdminService(
            TimetableSlotRepository timetableSlotRepository,
            PlaceService placeService,
            ClubService clubService
    ) {
        this.timetableSlotRepository = timetableSlotRepository;
        this.placeService = placeService;
        this.clubService = clubService;
    }

    /** 운영자 화면용 전체 목록. 표시 순서대로 */
    public List<AdminTimetableSlotResponse> getSlots() {
        return timetableSlotRepository.findAllWithStageOrderBySortOrder().stream()
                .map(AdminTimetableSlotResponse::from)
                .toList();
    }

    /**
     * 공연을 등록한다.
     * <p>검증: 무대는 category=STAGE 장소만, 종료는 시작보다 뒤, clubId가 있으면 존재하는 동아리(FR-TT-01, FR-ADM-06).
     * @throws CustomException PLACE_NOT_FOUND, TIMETABLE_STAGE_INVALID, TIMETABLE_INVALID_TIME, CLUB_NOT_FOUND
     */
    @Transactional
    public AdminTimetableSlotResponse create(CreateTimetableSlotRequest request) {
        validateTimes(request.startAt(), request.endAt());
        TimetableSlot slot = TimetableSlot.builder()
                .sortOrder(request.sortOrder())
                .title(request.title())
                .slotType(request.slotType())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .stage(getStage(request.stagePlaceId()))
                .club(getClub(request.clubId()))
                .build();
        return AdminTimetableSlotResponse.from(timetableSlotRepository.save(slot));
    }

    /**
     * 공연명·구분·무대·동아리 연결을 수정한다. 시각은 {@link #changeTimes}로만 바꾼다(변경 기록 때문).
     * @throws CustomException TIMETABLE_SLOT_NOT_FOUND, PLACE_NOT_FOUND, TIMETABLE_STAGE_INVALID, CLUB_NOT_FOUND
     */
    @Transactional
    public AdminTimetableSlotResponse update(Long slotId, UpdateTimetableSlotRequest request) {
        TimetableSlot slot = getSlotOrThrow(slotId);
        slot.update(request.title(), request.slotType(), getStage(request.stagePlaceId()), getClub(request.clubId()));
        return AdminTimetableSlotResponse.from(slot);
    }

    /**
     * 공연을 삭제한다. 이 공연을 고른 인스타팅 신청의 wanted_slot_id는 DB FK가 null로 만든다.
     * @throws CustomException TIMETABLE_SLOT_NOT_FOUND
     */
    @Transactional
    public void delete(Long slotId) {
        timetableSlotRepository.delete(getSlotOrThrow(slotId));
    }

    /**
     * 시작·종료 시각을 바꾼다. 시작 시각이 처음 바뀔 때만 원래 시각이 기록된다(FR-TT-04).
     * @throws CustomException TIMETABLE_SLOT_NOT_FOUND, TIMETABLE_INVALID_TIME
     */
    @Transactional
    public AdminTimetableSlotResponse changeTimes(Long slotId, ChangeSlotTimesRequest request) {
        validateTimes(request.startAt(), request.endAt());
        TimetableSlot slot = getSlotOrThrow(slotId);
        slot.changeTimes(request.startAt(), request.endAt());
        return AdminTimetableSlotResponse.from(slot);
    }

    /**
     * 지연 분을 넣거나(null) 해제한다(FR-TT-03).
     * @throws CustomException TIMETABLE_SLOT_NOT_FOUND
     */
    @Transactional
    public AdminTimetableSlotResponse setDelay(Long slotId, DelaySlotRequest request) {
        TimetableSlot slot = getSlotOrThrow(slotId);
        slot.delay(request.delayMinutes());
        return AdminTimetableSlotResponse.from(slot);
    }

    /**
     * 진행 중을 수동 지정하거나 해제한다. 지정은 한 번에 한 공연만이므로 지정 시 다른 공연의 지정을 모두 푼다(FR-TT-03).
     * @throws CustomException TIMETABLE_SLOT_NOT_FOUND
     */
    @Transactional
    public AdminTimetableSlotResponse setLive(Long slotId, LiveSlotRequest request) {
        TimetableSlot slot = getSlotOrThrow(slotId);
        if (request.live()) {
            timetableSlotRepository.findAllByLiveOverrideTrue().forEach(TimetableSlot::clearLive);
            slot.markLive();
        } else {
            slot.clearLive();
        }
        return AdminTimetableSlotResponse.from(slot);
    }

    /**
     * 순서를 일괄 변경한다. 목록은 모든 공연을 정확히 한 번씩 담아야 하며, 나열 순서대로 1부터 번호를 다시 매긴다.
     * @throws CustomException TIMETABLE_ORDER_INVALID
     */
    @Transactional
    public List<AdminTimetableSlotResponse> reorder(ReorderSlotsRequest request) {
        Map<Long, TimetableSlot> slotsById = timetableSlotRepository.findAllWithStageOrderBySortOrder().stream()
                .collect(Collectors.toMap(TimetableSlot::getId, Function.identity()));
        // 빠지거나 중복된 ID가 있으면 순서 번호가 겹치거나 비므로 전체가 한 번씩인지 먼저 본다
        Set<Long> requested = new HashSet<>(request.slotIds());
        if (requested.size() != request.slotIds().size() || !requested.equals(slotsById.keySet())) {
            throw new CustomException(ErrorCode.TIMETABLE_ORDER_INVALID);
        }
        for (int i = 0; i < request.slotIds().size(); i++) {
            slotsById.get(request.slotIds().get(i)).reorder(i + 1);
        }
        return request.slotIds().stream()
                .map(slotsById::get)
                .map(AdminTimetableSlotResponse::from)
                .toList();
    }

    /**
     * 동아리가 삭제될 때 그 동아리의 공연에서 연결만 끊는다. 공연 자체는 남는다.
     * <p>라인업 운영자 서비스가 삭제 직전에 호출한다(다른 도메인 Repository 주입 금지).
     */
    @Transactional
    public void detachClub(Long clubId) {
        timetableSlotRepository.findAllByClub_Id(clubId).forEach(TimetableSlot::detachClub);
    }

    private TimetableSlot getSlotOrThrow(Long slotId) {
        return timetableSlotRepository.findById(slotId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIMETABLE_SLOT_NOT_FOUND));
    }

    // 무대는 지도 장소 중 STAGE만. 장소 조회는 place 도메인 서비스를 거친다(다른 도메인 Repository 주입 금지)
    private Place getStage(Long placeId) {
        Place place = placeService.getPlaceEntity(placeId);
        if (place.getCategory() != PlaceCategory.STAGE) {
            throw new CustomException(ErrorCode.TIMETABLE_STAGE_INVALID);
        }
        return place;
    }

    // clubId는 선택값. 있으면 라인업 도메인 서비스로 존재를 확인한다
    private Club getClub(Long clubId) {
        return clubId == null ? null : clubService.getClubEntity(clubId);
    }

    private static void validateTimes(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new CustomException(ErrorCode.TIMETABLE_INVALID_TIME);
        }
    }
}
