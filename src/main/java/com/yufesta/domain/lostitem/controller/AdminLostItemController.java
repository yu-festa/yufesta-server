package com.yufesta.domain.lostitem.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.controller.api.AdminLostItemApi;
import com.yufesta.domain.lostitem.dto.request.CreateOfficialLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.UpdateLostItemVisibilityRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.service.LostItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 분실물 관리 API. 명세는 AdminLostItemApi */
@RestController
public class AdminLostItemController implements AdminLostItemApi {

    private final LostItemService lostItemService;

    public AdminLostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    @Override
    public ResponseEntity<ApiResponse<LostItemResponse>> createOfficialLostItem(
            Long userId,
            CreateOfficialLostItemRequest request
    ) {
        LostItemResponse lostItem = lostItemService.createOfficial(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "안내소 습득물을 등록했습니다.", lostItem));
    }

    @Override
    public ApiResponse<LostItemResponse> resolveLostItem(Long userId, Long lostItemId) {
        return ApiResponse.success("분실물 게시글을 해결 처리했습니다.", lostItemService.resolveByAdmin(userId, lostItemId));
    }

    @Override
    public ApiResponse<LostItemResponse> updateLostItemVisibility(
            Long userId,
            Long lostItemId,
            UpdateLostItemVisibilityRequest request
    ) {
        LostItemResponse lostItem = lostItemService.updateVisibility(userId, lostItemId, request);
        String message = request.hidden() ? "분실물 게시글을 숨겼습니다." : "분실물 게시글을 복구했습니다.";
        return ApiResponse.success(message, lostItem);
    }
}
