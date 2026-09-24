package com.yufesta.domain.notice.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.notice.dto.response.NoticeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 공지 공개 API 명세
 */
@Tag(name = "Notice", description = "공지 (FR-NT)")
@RequestMapping("/api/v1/notices")
public interface NoticeApi {

    @Operation(summary = "공지 목록", description = "최신순으로 반환한다. 로그인 불필요. FR-NT-01")
    @GetMapping
    ApiResponse<List<NoticeResponse>> getNotices(
            @Parameter(description = "가져올 개수(1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    );

    @Operation(summary = "공지 상세", description = "로그인 불필요. FR-NT-01")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND")
    @GetMapping("/{noticeId}")
    ApiResponse<NoticeResponse> getNotice(@PathVariable Long noticeId);
}
