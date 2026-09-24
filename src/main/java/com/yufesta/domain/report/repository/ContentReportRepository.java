package com.yufesta.domain.report.repository;

import com.yufesta.domain.report.entity.ContentReport;
import com.yufesta.domain.report.enums.ContentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

/** 콘텐츠 신고 기록 조회와 저장을 담당 */
public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    boolean existsByTargetTypeAndTargetIdAndReporter_Id(ContentTargetType targetType, Long targetId, Long reporterId);
}
