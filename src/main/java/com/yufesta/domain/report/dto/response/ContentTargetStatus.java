package com.yufesta.domain.report.dto.response;

/** 운영자 신고 목록에 표시할 신고 대상 콘텐츠의 현재 상태 */
public record ContentTargetStatus(
        int reportCount,
        boolean hidden
) {
}
