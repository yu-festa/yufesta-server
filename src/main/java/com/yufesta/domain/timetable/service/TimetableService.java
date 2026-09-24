package com.yufesta.domain.timetable.service;

import com.yufesta.domain.timetable.dto.response.TimetableResponse;
import com.yufesta.domain.timetable.dto.response.TimetableSlotResponse;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.repository.TimetableSlotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 타임테이블 공개 조회. LIVE 판정과 지연 반영 시각 계산을 여기서 끝내고 프론트는 그대로 그린다
 */
@Service
@Transactional(readOnly = true)
public class TimetableService {

    private final TimetableSlotRepository timetableSlotRepository;
    private final Clock clock;

    public TimetableService(TimetableSlotRepository timetableSlotRepository, Clock clock) {
        this.timetableSlotRepository = timetableSlotRepository;
        this.clock = clock;
    }

    /**
     * 공연 목록을 표시 순서대로 반환한다.
     * <p>LIVE 판정(FR-TT-02·03): 운영자가 수동 지정한 항목이 하나라도 있으면 그 항목만 LIVE이고, 없으면 시계 기준
     * (실제 시작 ≤ now < 실제 종료). 실제 시각은 원래 시각에 지연 분을 더한 값이다(FR-TT-04).
     */
    public TimetableResponse getTimetable() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TimetableSlot> slots = timetableSlotRepository.findAllWithStageOrderBySortOrder();
        boolean overridden = slots.stream().anyMatch(TimetableSlot::isLiveOverride);

        List<TimetableSlotResponse> items = slots.stream()
                .map(slot -> TimetableSlotResponse.of(slot, isLive(slot, overridden, now)))
                .toList();
        return TimetableResponse.of(now, items);
    }

    /**
     * 동아리가 연결된 공연을 표시 순서대로 준다. 라인업 카드가 공연 시간·무대를 붙일 때 쓴다(FR-LU-01).
     * 호출 측이 club id로 묶으며, 무대·동아리는 함께 읽혀 있다.
     */
    public List<TimetableSlot> getSlotsWithClub() {
        return timetableSlotRepository.findAllWithClubOrderBySortOrder();
    }

    // 현장 진행이 시간표와 어긋날 때는 운영자 판단이 시계보다 우선한다(FR-TT-03)
    private static boolean isLive(TimetableSlot slot, boolean overridden, LocalDateTime now) {
        return overridden ? slot.isLiveOverride() : slot.isLiveAt(now);
    }
}
