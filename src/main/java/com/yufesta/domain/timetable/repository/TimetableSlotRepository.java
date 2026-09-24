package com.yufesta.domain.timetable.repository;

import com.yufesta.domain.timetable.entity.TimetableSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {

    // 목록 응답에 무대 이름이 들어가므로 한 번에 읽는다(N+1 방지). 순서가 같으면 시작 시각순
    @Query("select s from TimetableSlot s join fetch s.stage order by s.sortOrder asc, s.startAt asc")
    List<TimetableSlot> findAllWithStageOrderBySortOrder();

    List<TimetableSlot> findAllByLiveOverrideTrue();
}
