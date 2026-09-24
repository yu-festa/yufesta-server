package com.yufesta.domain.cheer.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.cheer.dto.request.UpdateCheerVisibilityRequest;
import com.yufesta.domain.cheer.dto.response.AdminCheerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** 운영자 응원 메시지 관리 API 명세. STAFF 이상은 SecurityConfig에서 인가한다. */
@Tag(name = "Admin Cheer", description = "운영자 응원 메시지 관리 (FR-CH-03, FR-ADM-04, STAFF)")
@RequestMapping("/api/v1/admin/cheers")
public interface AdminCheerApi {

    @Operation(summary = "응원 메시지 숨김 또는 복구", description = "STAFF 또는 OWNER가 응원 메시지를 숨기거나 다시 공개한다. CSRF 토큰이 필요하다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CHEER_NOT_FOUND")
    @PatchMapping("/{cheerId}/visibility")
    ApiResponse<AdminCheerResponse> updateVisibility(
            @PathVariable Long cheerId,
            @Valid @RequestBody UpdateCheerVisibilityRequest request
    );
}
