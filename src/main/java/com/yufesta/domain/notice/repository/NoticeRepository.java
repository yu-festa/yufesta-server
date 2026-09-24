package com.yufesta.domain.notice.repository;

import com.yufesta.domain.notice.entity.Notice;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    List<Notice> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
