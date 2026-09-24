package com.yufesta.domain.notice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 공지 수정 요청 */
@Builder
public record UpdateNoticeRequest(
        @Schema(description = "공지 제목", example = "우천 시 공연 안내")
        @NotBlank
        @Size(max = 100)
        String title,

        @Schema(description = "공지 본문", example = "우천 시 공연은 학생회관 1층에서 진행됩니다.")
        @NotBlank
        String body,

        @Schema(description = "홈 상단 긴급 배너 노출 여부", example = "false")
        boolean banner
) {
}
