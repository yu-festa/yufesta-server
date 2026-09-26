package com.yufesta.domain.lostitem.comment.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.comment.dto.request.UpdateLostItemCommentVisibilityRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 운영자 분실물 댓글 관리 API 명세 */
@Tag(name = "Admin Lost Item Comment", description = "운영자 분실물 댓글 관리 (FR-ADM-04)")
@RequestMapping("/api/v1/admin/lost-items/{lostItemId}/comments")
@SecurityRequirement(name = "cookieAuth")
public interface AdminLostItemCommentApi {

    @Operation(summary = "분실물 댓글 숨김 또는 복구", description = "STAFF 또는 OWNER가 분실물 댓글 또는 답글을 숨기거나 복구한다. CSRF 토큰이 필요하다.")
    @PatchMapping("/{commentId}/visibility")
    ApiResponse<LostItemCommentResponse> updateCommentVisibility(
            @PathVariable Long lostItemId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateLostItemCommentVisibilityRequest request
    );
}
