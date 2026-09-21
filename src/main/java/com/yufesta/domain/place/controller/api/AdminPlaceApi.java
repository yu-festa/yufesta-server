package com.yufesta.domain.place.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.place.dto.request.CreatePlaceEventRequest;
import com.yufesta.domain.place.dto.request.CreatePlaceRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceEventRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceRequest;
import com.yufesta.domain.place.dto.response.AdminPlaceResponse;
import com.yufesta.domain.place.dto.response.PlaceEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 운영자 장소 관리 API 명세 */
@Tag(name = "Admin Place", description = "운영자 장소와 장소 이벤트 관리 (FR-ADM)")
@RequestMapping("/api/v1/admin/places")
@SecurityRequirement(name = "cookieAuth")
public interface AdminPlaceApi {

    @Operation(summary = "장소 등록", description = "STAFF 또는 OWNER만 등록할 수 있다. CSRF 토큰이 필요하다. FR-ADM-08")
    @PostMapping
    ResponseEntity<ApiResponse<AdminPlaceResponse>> createPlace(
            @Valid @RequestBody CreatePlaceRequest request
    );

    @Operation(summary = "장소 수정", description = "STAFF 또는 OWNER만 수정할 수 있다. 비노출 장소도 수정할 수 있다. CSRF 토큰이 필요하다. FR-ADM-08")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND")
    @PatchMapping("/{placeId}")
    ApiResponse<AdminPlaceResponse> updatePlace(
            @PathVariable Long placeId,
            @Valid @RequestBody UpdatePlaceRequest request
    );

    @Operation(summary = "장소 이벤트 등록", description = "STAFF 또는 OWNER만 장소에 연결할 이벤트를 등록할 수 있다. CSRF 토큰이 필요하다. FR-ADM-08")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND")
    @PostMapping("/{placeId}/events")
    ResponseEntity<ApiResponse<PlaceEventResponse>> createPlaceEvent(
            @PathVariable Long placeId,
            @Valid @RequestBody CreatePlaceEventRequest request
    );

    @Operation(summary = "장소 이벤트 수정", description = "STAFF 또는 OWNER만 해당 장소에 연결된 이벤트를 수정할 수 있다. CSRF 토큰이 필요하다. FR-ADM-08")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "PLACE_NOT_FOUND 또는 PLACE_EVENT_NOT_FOUND")
    @PatchMapping("/{placeId}/events/{placeEventId}")
    ApiResponse<PlaceEventResponse> updatePlaceEvent(
            @PathVariable Long placeId,
            @PathVariable Long placeEventId,
            @Valid @RequestBody UpdatePlaceEventRequest request
    );
}
