package com.yufesta.domain.lostitem.repository;

import com.yufesta.domain.lostitem.entity.LostItem;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분실물 게시글 공개 조회와 저장을 담당 */
public interface LostItemRepository extends JpaRepository<LostItem, Long> {

    List<LostItem> findAllByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);
}
