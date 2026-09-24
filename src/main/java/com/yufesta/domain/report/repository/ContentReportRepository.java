package com.yufesta.domain.report.repository;

import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 콘텐츠 신고 기록 조회와 저장을 담당 */
public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType targetType, Long targetId, Long reporterId);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findAllByReviewedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findAllByReviewedAtIsNotNullOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findAllByTargetTypeOrderByCreatedAtDesc(ContentTargetType targetType, Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findAllByTargetTypeAndReviewedAtIsNullOrderByCreatedAtDesc(
            ContentTargetType targetType,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findAllByTargetTypeAndReviewedAtIsNotNullOrderByCreatedAtDesc(
            ContentTargetType targetType,
            Pageable pageable
    );
}
