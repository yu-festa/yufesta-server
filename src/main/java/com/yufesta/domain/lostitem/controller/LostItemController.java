package com.yufesta.domain.lostitem.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.controller.api.LostItemApi;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.service.LostItemService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 분실물 공개 API. 명세는 LostItemApi */
@RestController
public class LostItemController implements LostItemApi {

    private final LostItemService lostItemService;

    public LostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    @Override
    public ApiResponse<List<LostItemResponse>> getLostItems(int size) {
        return ApiResponse.success(lostItemService.getLostItems(size));
    }

    @Override
    public ResponseEntity<ApiResponse<LostItemResponse>> createLostItem(Long userId, CreateLostItemRequest request) {
        LostItemResponse lostItem = lostItemService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "분실물 게시글을 등록했습니다.", lostItem));
    }

    @Override
    public ApiResponse<LostItemResponse> resolveLostItem(Long userId, Long lostItemId) {
        return ApiResponse.success(lostItemService.resolve(userId, lostItemId));
    }

    @Override
    public ResponseEntity<Void> deleteLostItem(Long userId, Long lostItemId) {
        lostItemService.delete(userId, lostItemId);
        return ResponseEntity.noContent().build();
    }
}
