package com.yufesta.domain.lostitem.comment.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.comment.dto.request.CreateLostItemCommentRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 분실물 댓글·답글 공개 API 명세 */
@Tag(name = "Lost Item Comment", description = "분실물 댓글·답글 (FR-LF)")
@RequestMapping("/api/v1/lost-items/{lostItemId}/comments")
public interface LostItemCommentApi {

    @Operation(summary = "분실물 댓글 조회", description = "노출 중인 분실물 글의 최상위 댓글과 답글을 작성 시각순으로 반환한다. 로그인 불필요.")
    @GetMapping
    ApiResponse<List<LostItemCommentResponse>> getComments(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId
    );

    @Operation(summary = "분실물 댓글 작성", description = "로그인 사용자가 최상위 댓글을 작성한다. CSRF 토큰이 필요하다.")
    @PostMapping
    ResponseEntity<ApiResponse<LostItemCommentResponse>> createComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId,
            @Valid @RequestBody CreateLostItemCommentRequest request
    );

    @Operation(summary = "분실물 답글 작성", description = "로그인 사용자가 최상위 댓글에 답글을 작성한다. 답글의 답글은 지원하지 않는다. CSRF 토큰이 필요하다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "LOST_ITEM_COMMENT_REPLY_NOT_ALLOWED")
    @PostMapping("/{commentId}/replies")
    ResponseEntity<ApiResponse<LostItemCommentResponse>> createReply(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId,
            @PathVariable Long commentId,
            @Valid @RequestBody CreateLostItemCommentRequest request
    );

    @Operation(summary = "내 분실물 댓글 삭제", description = "작성자 본인이 자신의 댓글 또는 답글을 소프트 삭제한다. CSRF 토큰이 필요하다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_COMMENT_NOT_FOUND")
    @DeleteMapping("/{commentId}")
    ResponseEntity<Void> deleteComment(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId,
            @PathVariable Long commentId
    );
}
