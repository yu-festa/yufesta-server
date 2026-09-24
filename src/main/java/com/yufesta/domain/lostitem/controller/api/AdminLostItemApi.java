package com.yufesta.domain.lostitem.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.dto.request.CreateOfficialLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.UpdateLostItemVisibilityRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 운영자 분실물 관리 API 명세 */
@Tag(name = "Admin Lost Item", description = "운영자 분실물 관리 (FR-LF-04, 06 · FR-ADM-04)")
@RequestMapping("/api/v1/admin/lost-items")
@SecurityRequirement(name = "cookieAuth")
public interface AdminLostItemApi {

    @Operation(summary = "안내소 습득물 등록", description = "STAFF 또는 OWNER가 안내소 보관 습득물을 공식 게시글로 등록한다. CSRF 토큰이 필요하다. FR-LF-06")
    @PostMapping
    ResponseEntity<ApiResponse<LostItemResponse>> createOfficialLostItem(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateOfficialLostItemRequest request
    );

    @Operation(summary = "분실물 해결 처리", description = "STAFF 또는 OWNER가 일반 또는 공식 분실물 게시글을 해결 처리한다. CSRF 토큰이 필요하다. FR-LF-04, 06")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_NOT_FOUND")
    @PatchMapping("/{lostItemId}/resolve")
    ApiResponse<LostItemResponse> resolveLostItem(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId
    );

    @Operation(summary = "분실물 숨김 또는 복구", description = "STAFF 또는 OWNER가 일반 사용자 분실물 게시글을 숨기거나 복구한다. 공식 안내소 글은 제외된다. CSRF 토큰이 필요하다. FR-LF-04")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_NOT_FOUND")
    @PatchMapping("/{lostItemId}/visibility")
    ApiResponse<LostItemResponse> updateLostItemVisibility(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId,
            @Valid @RequestBody UpdateLostItemVisibilityRequest request
    );
}
