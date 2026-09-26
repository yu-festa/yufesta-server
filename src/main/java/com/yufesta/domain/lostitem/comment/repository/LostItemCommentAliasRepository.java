package com.yufesta.domain.lostitem.comment.repository;

import com.yufesta.domain.lostitem.comment.entity.LostItemCommentAlias;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분실물 글 단위 익명 닉네임을 관리한다. */
public interface LostItemCommentAliasRepository extends JpaRepository<LostItemCommentAlias, Long> {

    Optional<LostItemCommentAlias> findByLostItem_IdAndUser_Id(Long lostItemId, Long userId);

    boolean existsByLostItem_IdAndDisplayName(Long lostItemId, String displayName);
}
