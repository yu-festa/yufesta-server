package com.yufesta.domain.place.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.place.controller.api.AdminPlaceApi;
import com.yufesta.domain.place.dto.request.CreatePlaceRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceRequest;
import com.yufesta.domain.place.dto.response.AdminPlaceResponse;
import com.yufesta.domain.place.service.PlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 장소 관리 API. 명세는 AdminPlaceApi */
@RestController
public class AdminPlaceController implements AdminPlaceApi {

    private final PlaceService placeService;

    public AdminPlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @Override
    public ResponseEntity<ApiResponse<AdminPlaceResponse>> createPlace(CreatePlaceRequest request) {
        AdminPlaceResponse response = placeService.createPlace(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "장소를 등록했습니다.", response));
    }

    @Override
    public ApiResponse<AdminPlaceResponse> updatePlace(Long placeId, UpdatePlaceRequest request) {
        return ApiResponse.success("장소를 수정했습니다.", placeService.updatePlace(placeId, request));
    }

}
