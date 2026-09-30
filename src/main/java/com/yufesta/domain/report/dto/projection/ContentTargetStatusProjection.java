package com.yufesta.domain.report.dto.projection;

import com.yufesta.domain.report.dto.response.ContentTargetStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 신고 대상의 현재 상태만 일괄 조회하는 읽기 전용 프로젝션 */
public record ContentTargetStatusProjection(
        Long targetId,
        int reportCount,
        boolean hidden
) {

    public ContentTargetStatus toStatus() {
        return new ContentTargetStatus(reportCount, hidden);
    }

    public static Map<Long, ContentTargetStatus> indexByTargetId(
            List<ContentTargetStatusProjection> projections
    ) {
        return projections.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ContentTargetStatusProjection::targetId,
                        ContentTargetStatusProjection::toStatus
                ));
    }
}
