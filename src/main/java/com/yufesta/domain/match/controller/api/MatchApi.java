package com.yufesta.domain.match.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.dto.request.ApplyMatchRequest;
import com.yufesta.domain.match.dto.request.UpdateApplicationRequest;
import com.yufesta.domain.match.dto.response.ApplicationResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MatchTagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 인스타팅 공개 API 명세
 */
@Tag(name = "Match", description = "인스타팅 (FR-MT)")
@RequestMapping("/api/v1/match")
public interface MatchApi {

    @Operation(
            summary = "홈 인스타팅 블록",
            description = "서버 시각, 현재·다음 회차, 신청자 수를 준다. 로그인 시 내 신청 여부와 최근 발표 결과(my)가 함께 온다. "
                    + "카운트다운은 serverNow와 회차 시각으로 계산한다. 로그인 선택. FR-MT-50~56"
    )
    @GetMapping("/summary")
    ApiResponse<MatchSummaryResponse> getSummary(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(summary = "관심 태그 목록", description = "신청 화면 선택지. 고정 10개, 최대 3개 선택. 로그인 불필요. FR-MT-10")
    @GetMapping("/tags")
    ApiResponse<List<MatchTagResponse>> getTags();

    @Operation(
            summary = "인스타팅 신청",
            description = "현재 회차에 신청한다. 접수 중(OPEN, 마감 전)에만 가능. 취소했던 신청이 있으면 그 행을 되살린다. "
                    + "인스타 ID는 @ 제거·소문자로 정규화되며 회차 내 유니크, 닉네임은 중복 허용. 로그인 필요, X-XSRF-TOKEN 헤더 필요. FR-MT-10~13"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_NOT_OPEN, APPLICATION_ALREADY_EXISTS, APPLICATION_INSTAGRAM_DUPLICATE")
    @PostMapping("/applications")
    ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ApplyMatchRequest request
    );

    @Operation(summary = "내 신청 조회", description = "현재 회차의 내 신청. 취소했거나 없으면 404. 로그인 필요. FR-MT-30")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "APPLICATION_NOT_FOUND")
    @GetMapping("/applications/me")
    ApiResponse<ApplicationResponse> getMyApplication(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(summary = "내 신청 수정", description = "마감 전에만 가능. 인스타 ID 유니크를 다시 검사한다. 동의 항목은 수정 불가. 로그인 필요, X-XSRF-TOKEN 헤더 필요. FR-MT-14")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_NOT_OPEN, APPLICATION_INSTAGRAM_DUPLICATE")
    @PatchMapping("/applications/me")
    ApiResponse<ApplicationResponse> updateMyApplication(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateApplicationRequest request
    );

    @Operation(summary = "내 신청 취소", description = "마감 전에만 가능. 소프트 삭제이며 다시 신청하면 같은 신청이 되살아난다. 로그인 필요, X-XSRF-TOKEN 헤더 필요. FR-MT-14")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_NOT_OPEN")
    @DeleteMapping("/applications/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelMyApplication(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
