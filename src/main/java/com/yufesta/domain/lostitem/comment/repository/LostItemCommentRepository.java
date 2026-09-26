package com.yufesta.domain.lostitem.comment.repository;

import com.yufesta.domain.lostitem.comment.entity.LostItemComment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 분실물 댓글과 답글을 조회·저장한다. */
public interface LostItemCommentRepository extends JpaRepository<LostItemComment, Long> {

    @EntityGraph(attributePaths = {"author", "parent"})
    List<LostItemComment> findAllByLostItem_IdAndHiddenFalseOrderByCreatedAtAsc(Long lostItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LostItemComment c where c.id = :commentId")
    Optional<LostItemComment> findByIdForUpdate(@Param("commentId") Long commentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LostItemComment c set c.reportCount = c.reportCount + 1 where c.id = :commentId")
    int incrementReportCount(@Param("commentId") Long commentId);
}
