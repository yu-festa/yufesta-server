package com.yufesta.domain.place.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.place.controller.api.PlaceApi;
import com.yufesta.domain.place.dto.response.PlaceDetailResponse;
import com.yufesta.domain.place.dto.response.PlaceListResponse;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.service.PlaceService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지도 장소 공개 API. 명세는 PlaceApi
 */
@RestController
public class PlaceController implements PlaceApi {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @Override
    public ApiResponse<List<PlaceListResponse>> getPlaces(
            PlaceCategory category
    ) {
        return ApiResponse.success(placeService.getPlaces(category));
    }

    @Override
    public ApiResponse<PlaceDetailResponse> getPlace(Long placeId) {
        return ApiResponse.success(placeService.getPlace(placeId));
    }
}
