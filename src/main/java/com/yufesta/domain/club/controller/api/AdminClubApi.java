package com.yufesta.domain.club.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.club.dto.request.CreateClubRequest;
import com.yufesta.domain.club.dto.request.UpdateClubRequest;
import com.yufesta.domain.club.dto.response.AdminClubResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 운영자 라인업 관리 API 명세 */
@Tag(name = "Admin Club", description = "운영자 라인업 카드 관리 (FR-ADM-07)")
@RequestMapping("/api/v1/admin/clubs")
@SecurityRequirement(name = "cookieAuth")
public interface AdminClubApi {

    @Operation(summary = "동아리 목록(운영자)", description = "저장된 컬럼 그대로. 표시 순서·이름순. STAFF")
    @GetMapping
    ApiResponse<List<AdminClubResponse>> getClubs();

    @Operation(summary = "동아리 등록", description = "동아리가 제공한 자료만 등록한다(FR-LU-04). 사진은 URL 문자열(업로드 API는 추후). STAFF, X-XSRF-TOKEN 헤더 필요. FR-ADM-07")
    @PostMapping
    ResponseEntity<ApiResponse<AdminClubResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateClubRequest request
    );

    @Operation(summary = "동아리 수정", description = "전체 필드를 다시 보낸다. STAFF, X-XSRF-TOKEN 헤더 필요. FR-ADM-07")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CLUB_NOT_FOUND")
    @PatchMapping("/{clubId}")
    ApiResponse<AdminClubResponse> update(
            @PathVariable Long clubId,
            @Valid @RequestBody UpdateClubRequest request
    );

    @Operation(summary = "동아리 삭제", description = "연결된 타임테이블 공연은 남고 동아리 연결만 비워진다. STAFF, X-XSRF-TOKEN 헤더 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CLUB_NOT_FOUND")
    @DeleteMapping("/{clubId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long clubId);
}
