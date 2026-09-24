package com.yufesta.domain.lostitem.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 분실물 공개 API 명세 */
@Tag(name = "Lost Item", description = "분실물 (FR-LF)")
@RequestMapping("/api/v1/lost-items")
public interface LostItemApi {

    @Operation(summary = "분실물 목록", description = "최신순으로 반환한다. 로그인 불필요. FR-LF-01")
    @GetMapping
    ApiResponse<List<LostItemResponse>> getLostItems(
            @Parameter(description = "가져올 개수(1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    );

    @Operation(summary = "분실물 작성", description = "로그인 사용자가 분실물 또는 습득물 게시글을 작성한다. CSRF 토큰이 필요하다. FR-LF-02")
    @PostMapping
    ResponseEntity<ApiResponse<LostItemResponse>> createLostItem(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateLostItemRequest request
    );
}
