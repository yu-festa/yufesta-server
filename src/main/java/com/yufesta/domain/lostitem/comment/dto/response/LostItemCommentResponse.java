package com.yufesta.domain.lostitem.comment.dto.response;

import com.yufesta.domain.lostitem.comment.entity.LostItemComment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/** 공개 분실물 댓글 응답. 사용자 ID는 공개하지 않는다. */
@Builder
public record LostItemCommentResponse(
        Long id,
        Long parentId,
        String content,
        String displayName,
        @Schema(description = "현재 로그인 사용자가 작성한 댓글인지 여부") boolean mine,
        @Schema(description = "분실물 원글 작성자가 작성한 댓글인지 여부") boolean postAuthor,
        @Schema(description = "작성자 삭제 여부") boolean deleted,
        LocalDateTime createdAt,
        List<LostItemCommentResponse> replies
) {

    public static LostItemCommentResponse from(
            LostItemComment comment,
            Long userId,
            Long lostItemAuthorId,
            List<LostItemCommentResponse> replies
    ) {
        Long commentAuthorId = comment.getAuthor() == null ? null : comment.getAuthor().getId();
        return LostItemCommentResponse.builder()
                .id(comment.getId())
                .parentId(comment.getParent() == null ? null : comment.getParent().getId())
                .content(comment.getDisplayContent())
                .displayName(comment.getDisplayName())
                .mine(comment.isOwnedBy(userId))
                .postAuthor(lostItemAuthorId != null && lostItemAuthorId.equals(commentAuthorId))
                .deleted(comment.isDeleted())
                .createdAt(comment.getCreatedAt())
                .replies(replies)
                .build();
    }
}
