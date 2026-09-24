package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.response.MatchSlotsResponse;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.service.TimetableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인스타팅 "보고 싶은 공연"(FR-MT-05). 선택지 목록과 신청·수정 시 검증을 맡는다.
 * 규칙 자체는 MatchRound.allowsWantedSlot 한 곳이고, 복사·배치는 Application.wantedSlotFor로 같은 규칙을 쓴다
 */
@Service
@Transactional(readOnly = true)
public class WantedSlotService {

    private final MatchRoundService matchRoundService;
    private final TimetableService timetableService;

    public WantedSlotService(MatchRoundService matchRoundService, TimetableService timetableService) {
        this.matchRoundService = matchRoundService;
        this.timetableService = timetableService;
    }

    /**
     * 현재 회차 신청에서 고를 수 있는 공연 목록. 발표 시각 이후 시작 공연만(FR-MT-05).
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MatchSlotsResponse getSelectableSlots() {
        MatchRound round = matchRoundService.getCurrentRound();
        return MatchSlotsResponse.of(round, timetableService.getSlotsStartingAtOrAfter(round.getPublishAt()));
    }

    /**
     * 신청·수정에서 고른 공연을 확인한다. slotId가 null이면 "선택 없음"으로 null을 돌려준다.
     * @throws CustomException TIMETABLE_SLOT_NOT_FOUND, APPLICATION_SLOT_NOT_SELECTABLE
     */
    public TimetableSlot requireSelectable(MatchRound round, Long slotId) {
        if (slotId == null) {
            return null;
        }
        TimetableSlot slot = timetableService.getSlot(slotId);
        if (!round.allowsWantedSlot(slot.getStartAt())) {
            throw new CustomException(ErrorCode.APPLICATION_SLOT_NOT_SELECTABLE);
        }
        return slot;
    }
}
