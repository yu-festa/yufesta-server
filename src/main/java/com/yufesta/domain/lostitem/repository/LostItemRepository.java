package com.yufesta.domain.lostitem.repository;

import com.yufesta.domain.lostitem.entity.LostItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 분실물 게시글 공개 조회와 저장을 담당 */
public interface LostItemRepository extends JpaRepository<LostItem, Long> {

    @EntityGraph(attributePaths = "image")
    List<LostItem> findAllByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);

    /** 같은 게시글의 신고 처리 순서를 보장하기 위해 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LostItem l where l.id = :lostItemId")
    Optional<LostItem> findByIdForUpdate(@Param("lostItemId") Long lostItemId);

    /** 신고 수는 읽어서 더하지 않고 DB에서 원자적으로 증가시킨다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LostItem l set l.reportCount = l.reportCount + 1 where l.id = :lostItemId")
    int incrementReportCount(@Param("lostItemId") Long lostItemId);
}
