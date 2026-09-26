package com.yufesta.domain.lostitem.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemImageResponse;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

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

    @Operation(
            summary = "내 분실물 이미지 등록",
            description = "multipart 필드 file(jpeg·png, 10MB 이하)을 본인 게시글에 한 장만 등록한다. 숨김 글은 등록할 수 없다. "
                    + "CSRF 토큰이 필요하다. FR-LF-10"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "IMAGE_UNSUPPORTED_TYPE, IMAGE_INVALID")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_NOT_FOUND")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "LOST_ITEM_IMAGE_ALREADY_EXISTS")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "PHOTO_TOO_LARGE")
    @PostMapping(value = "/{lostItemId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<LostItemImageResponse>> uploadImage(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId,
            @RequestPart("file") MultipartFile file
    );

    @Operation(summary = "내 분실물 이미지 삭제", description = "본인 게시글의 이미지와 저장소 객체를 함께 삭제한다. CSRF 토큰이 필요하다. FR-LF-10")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_NOT_FOUND, LOST_ITEM_IMAGE_NOT_FOUND")
    @DeleteMapping("/{lostItemId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteImage(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId,
            @PathVariable Long imageId
    );

    @Operation(summary = "내 분실물 해결 처리", description = "작성자 본인이 자신의 게시글을 해결 처리한다. CSRF 토큰이 필요하다. FR-LF-05")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_NOT_FOUND")
    @PatchMapping("/{lostItemId}/resolve")
    ApiResponse<LostItemResponse> resolveLostItem(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId
    );

    @Operation(summary = "내 분실물 삭제", description = "작성자 본인이 자신의 게시글을 소프트 삭제한다. CSRF 토큰이 필요하다. FR-LF-05")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LOST_ITEM_NOT_FOUND")
    @DeleteMapping("/{lostItemId}")
    ResponseEntity<Void> deleteLostItem(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long lostItemId
    );
}
