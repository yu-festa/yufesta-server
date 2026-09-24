package com.yufesta.domain.timetable.repository;

import com.yufesta.domain.timetable.entity.TimetableSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {

    // 목록 응답에 무대 이름·동아리명이 들어가므로 한 번에 읽는다(N+1 방지). 동아리는 없을 수 있어 left join. 순서가 같으면 시작 시각순
    @Query("select s from TimetableSlot s join fetch s.stage left join fetch s.club order by s.sortOrder asc, s.startAt asc")
    List<TimetableSlot> findAllWithStageOrderBySortOrder();

    // 라인업 카드에 붙일 공연들. 동아리가 연결된 공연만(inner join)
    @Query("select s from TimetableSlot s join fetch s.stage join fetch s.club order by s.sortOrder asc, s.startAt asc")
    List<TimetableSlot> findAllWithClubOrderBySortOrder();

    List<TimetableSlot> findAllByClub_Id(Long clubId);

    List<TimetableSlot> findAllByLiveOverrideTrue();
}
