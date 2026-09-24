package com.yufesta.domain.cheer.repository;

import com.yufesta.domain.cheer.entity.Cheer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 공개 응원 메시지 조회와 저장을 담당 */
public interface CheerRepository extends JpaRepository<Cheer, Long> {

    List<Cheer> findAllByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);

    /** 같은 메시지의 신고 처리 순서를 보장하기 위해 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cheer c where c.id = :cheerId")
    Optional<Cheer> findByIdForUpdate(@Param("cheerId") Long cheerId);

    /** 신고 수는 읽어서 더하지 않고 DB에서 원자적으로 증가시킨다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Cheer c set c.reportCount = c.reportCount + 1 where c.id = :cheerId")
    int incrementReportCount(@Param("cheerId") Long cheerId);
}
