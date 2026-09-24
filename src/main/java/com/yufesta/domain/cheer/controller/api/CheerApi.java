package com.yufesta.domain.cheer.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.cheer.dto.request.CreateCheerRequest;
import com.yufesta.domain.cheer.dto.response.CheerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 응원 메시지 공개 API 명세 */
@Tag(name = "Cheer", description = "응원 메시지 (FR-CH)")
@RequestMapping("/api/v1/cheers")
public interface CheerApi {

    @Operation(summary = "응원 메시지 목록", description = "최신순으로 반환한다. 로그인 불필요. FR-CH-01")
    @GetMapping
    ApiResponse<List<CheerResponse>> getCheers(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(description = "가져올 개수(1~50)") @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    );

    @Operation(summary = "응원 메시지 작성", description = "로그인 없이 작성한다. 최초 작성 시 HttpOnly 익명 키 쿠키를 발급한다. CSRF 토큰이 필요하다. FR-CH-02")
    @PostMapping
    ResponseEntity<ApiResponse<CheerResponse>> createCheer(
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response,
            @Valid @RequestBody CreateCheerRequest cheerRequest
    );
}
