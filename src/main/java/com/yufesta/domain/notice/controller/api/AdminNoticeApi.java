package com.yufesta.domain.notice.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.notice.dto.request.CreateNoticeRequest;
import com.yufesta.domain.notice.dto.request.UpdateNoticeRequest;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 운영자 공지 관리 API 명세 */
@Tag(name = "Admin Notice", description = "운영자 공지 관리 (FR-NT)")
@RequestMapping("/api/v1/admin/notices")
@SecurityRequirement(name = "cookieAuth")
public interface AdminNoticeApi {

    @Operation(summary = "공지 등록", description = "STAFF 또는 OWNER만 등록할 수 있다. CSRF 토큰이 필요하다. FR-NT-01, 03")
    @PostMapping
    ResponseEntity<ApiResponse<NoticeResponse>> createNotice(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateNoticeRequest request
    );

    @Operation(summary = "공지 수정", description = "STAFF 또는 OWNER만 수정할 수 있다. CSRF 토큰이 필요하다. FR-NT-01, 03")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @PatchMapping("/{noticeId}")
    ApiResponse<NoticeResponse> updateNotice(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long noticeId,
            @Valid @RequestBody UpdateNoticeRequest request
    );

    @Operation(summary = "공지 삭제", description = "STAFF 또는 OWNER만 삭제할 수 있다. CSRF 토큰이 필요하다. FR-NT-01")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @DeleteMapping("/{noticeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteNotice(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long noticeId
    );
}
