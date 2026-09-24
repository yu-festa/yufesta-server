package com.yufesta.domain.cheer.repository;

import com.yufesta.domain.cheer.entity.Cheer;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 공개 응원 메시지 조회와 저장을 담당 */
public interface CheerRepository extends JpaRepository<Cheer, Long> {

    List<Cheer> findAllByHiddenFalseOrderByCreatedAtDesc(Pageable pageable);
}
