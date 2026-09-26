package com.yufesta.domain.lostitem.comment.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.comment.controller.api.LostItemCommentApi;
import com.yufesta.domain.lostitem.comment.dto.request.CreateLostItemCommentRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import com.yufesta.domain.lostitem.comment.service.LostItemCommentService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 분실물 댓글·답글 공개 API. 명세는 LostItemCommentApi */
@RestController
public class LostItemCommentController implements LostItemCommentApi {

    private final LostItemCommentService lostItemCommentService;

    public LostItemCommentController(LostItemCommentService lostItemCommentService) {
        this.lostItemCommentService = lostItemCommentService;
    }

    @Override
    public ApiResponse<List<LostItemCommentResponse>> getComments(Long userId, Long lostItemId) {
        return ApiResponse.success(lostItemCommentService.getComments(userId, lostItemId));
    }

    @Override
    public ResponseEntity<ApiResponse<LostItemCommentResponse>> createComment(
            Long userId,
            Long lostItemId,
            CreateLostItemCommentRequest request
    ) {
        LostItemCommentResponse comment = lostItemCommentService.create(userId, lostItemId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "댓글을 등록했습니다.", comment));
    }

    @Override
    public ResponseEntity<ApiResponse<LostItemCommentResponse>> createReply(
            Long userId,
            Long lostItemId,
            Long commentId,
            CreateLostItemCommentRequest request
    ) {
        LostItemCommentResponse reply = lostItemCommentService.reply(userId, lostItemId, commentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "답글을 등록했습니다.", reply));
    }

    @Override
    public ResponseEntity<Void> deleteComment(Long userId, Long lostItemId, Long commentId) {
        lostItemCommentService.delete(userId, lostItemId, commentId);
        return ResponseEntity.noContent().build();
    }
}
