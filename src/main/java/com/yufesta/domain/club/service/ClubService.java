package com.yufesta.domain.club.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.club.dto.response.ClubResponse;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.club.repository.ClubRepository;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.timetable.service.TimetableService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라인업 공개 조회. 카드마다 공연 시간·무대를 타임테이블에서 붙인다.
 * 다른 도메인(타임테이블)이 동아리를 연관으로 쓸 때도 이 서비스를 거친다
 */
@Service
@Transactional(readOnly = true)
public class ClubService {

    private final ClubRepository clubRepository;
    private final TimetableService timetableService;

    public ClubService(ClubRepository clubRepository, TimetableService timetableService) {
        this.clubRepository = clubRepository;
        this.timetableService = timetableService;
    }

    /**
     * 동아리 카드 목록을 표시 순서·이름순으로 준다(FR-LU-01~03).
     * <p>공연은 동아리가 연결된 슬롯을 한 번에 읽어 동아리별로 묶는다(N+1 없음). 공연이 없는 동아리는 빈 목록.
     */
    public List<ClubResponse> getClubs() {
        Map<Long, List<TimetableSlot>> performancesByClub = groupByClub(timetableService.getSlotsWithClub());
        return clubRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(club -> ClubResponse.of(club, performancesByClub.getOrDefault(club.getId(), List.of())))
                .toList();
    }

    /**
     * 동아리 카드 하나를 준다(FR-LU-01).
     * @throws CustomException CLUB_NOT_FOUND
     */
    public ClubResponse getClub(Long clubId) {
        Club club = getClubEntity(clubId);
        List<TimetableSlot> performances = timetableService.getSlotsWithClub().stream()
                .filter(slot -> slot.getClub().getId().equals(clubId))
                .toList();
        return ClubResponse.of(club, performances);
    }

    /**
     * 다른 도메인이 동아리를 연관으로 쓸 때 엔티티를 준다(타임테이블 공연의 club).
     * @throws CustomException CLUB_NOT_FOUND
     */
    public Club getClubEntity(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLUB_NOT_FOUND));
    }

    // 슬롯은 이미 표시 순서대로 와 있으므로 groupingBy가 그 순서를 보존한다
    private static Map<Long, List<TimetableSlot>> groupByClub(List<TimetableSlot> slots) {
        return slots.stream().collect(Collectors.groupingBy(slot -> slot.getClub().getId()));
    }
}
