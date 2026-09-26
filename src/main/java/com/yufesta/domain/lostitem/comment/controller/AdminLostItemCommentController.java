package com.yufesta.domain.lostitem.comment.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.comment.controller.api.AdminLostItemCommentApi;
import com.yufesta.domain.lostitem.comment.dto.request.UpdateLostItemCommentVisibilityRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import com.yufesta.domain.lostitem.comment.service.LostItemCommentService;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 분실물 댓글 관리 API. 명세는 AdminLostItemCommentApi */
@RestController
public class AdminLostItemCommentController implements AdminLostItemCommentApi {

    private final LostItemCommentService lostItemCommentService;

    public AdminLostItemCommentController(LostItemCommentService lostItemCommentService) {
        this.lostItemCommentService = lostItemCommentService;
    }

    @Override
    public ApiResponse<LostItemCommentResponse> updateCommentVisibility(
            Long lostItemId,
            Long commentId,
            UpdateLostItemCommentVisibilityRequest request
    ) {
        LostItemCommentResponse comment = lostItemCommentService.updateVisibility(lostItemId, commentId, request);
        String message = request.hidden() ? "분실물 댓글을 숨겼습니다." : "분실물 댓글을 복구했습니다.";
        return ApiResponse.success(message, comment);
    }
}
