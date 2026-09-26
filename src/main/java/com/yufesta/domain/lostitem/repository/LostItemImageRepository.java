package com.yufesta.domain.lostitem.repository;

import com.yufesta.domain.lostitem.entity.LostItemImage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 분실물 게시글 이미지 한 장을 저장하고 조회 */
public interface LostItemImageRepository extends JpaRepository<LostItemImage, Long> {

    boolean existsByLostItemId(Long lostItemId);

    Optional<LostItemImage> findByIdAndLostItemId(Long imageId, Long lostItemId);
}
