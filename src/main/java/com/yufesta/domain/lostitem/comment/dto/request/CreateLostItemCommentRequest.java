package com.yufesta.domain.lostitem.comment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 분실물 댓글 또는 답글 작성 요청 */
public record CreateLostItemCommentRequest(
        @Schema(description = "댓글 내용", example = "종합 안내소에 맡겨 두었습니다.")
        @NotBlank @Size(max = 200) String content
) {
}
