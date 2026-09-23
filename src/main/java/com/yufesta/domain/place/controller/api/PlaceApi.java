package com.yufesta.domain.place.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.place.dto.response.PlaceDetailResponse;
import com.yufesta.domain.place.dto.response.PlaceListResponse;
import com.yufesta.domain.place.enums.PlaceCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 지도 장소 공개 API 명세
 */
@Tag(name = "Place", description = "축제 지도 장소 (FR-MAP)")
@RequestMapping("/api/v1/places")
public interface PlaceApi {

    @Operation(summary = "장소 목록", description = "노출 중인 장소만 표시 순서와 이름순으로 반환한다. 사용자의 현재 위치가 있으면 프론트가 거리순으로 재정렬한다. 로그인 불필요. FR-MAP-01, 02, 06")
    @GetMapping
    ApiResponse<List<PlaceListResponse>> getPlaces(
            @Parameter(description = "장소 카테고리") @RequestParam(required = false) PlaceCategory category
    );

    @Operation(summary = "장소 상세", description = "장소 정보와 연결된 이벤트를 표시 순서대로 반환한다. 로그인 불필요. FR-MAP-03")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND")
    @GetMapping("/{placeId}")
    ApiResponse<PlaceDetailResponse> getPlace(@PathVariable Long placeId);
}
