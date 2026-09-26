package com.yufesta.domain.lostitem.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.controller.api.LostItemApi;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemImageResponse;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.service.LostItemImageService;
import com.yufesta.domain.lostitem.service.LostItemService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 분실물 공개 API. 명세는 LostItemApi */
@RestController
public class LostItemController implements LostItemApi {

    private final LostItemService lostItemService;
    private final LostItemImageService lostItemImageService;

    public LostItemController(LostItemService lostItemService, LostItemImageService lostItemImageService) {
        this.lostItemService = lostItemService;
        this.lostItemImageService = lostItemImageService;
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
    public ResponseEntity<ApiResponse<LostItemImageResponse>> uploadImage(Long userId, Long lostItemId, MultipartFile file) {
        LostItemImageResponse image = lostItemImageService.upload(userId, lostItemId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "분실물 이미지를 등록했습니다.", image));
    }

    @Override
    public void deleteImage(Long userId, Long lostItemId, Long imageId) {
        lostItemImageService.delete(userId, lostItemId, imageId);
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
