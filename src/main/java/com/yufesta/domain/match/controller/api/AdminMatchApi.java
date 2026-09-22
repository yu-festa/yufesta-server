package com.yufesta.domain.match.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.dto.request.UpdateRoundTimesRequest;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 인스타팅 운영자 API 명세. STAFF 이상, 발표만 OWNER(SecurityConfig). 쓰기는 X-XSRF-TOKEN 헤더 필요
 */
@Tag(name = "Admin Match", description = "인스타팅 회차 운영 (FR-MT, STAFF/OWNER)")
@RequestMapping("/api/v1/admin/match/rounds")
public interface AdminMatchApi {

    @Operation(summary = "회차 목록", description = "seq 순. 상태·시각·배치 실행/발표 시각 포함. STAFF")
    @GetMapping
    ApiResponse<List<AdminMatchRoundResponse>> getRounds();

    @Operation(summary = "회차 시각 수정", description = "close_at은 publish_at 10분 전, open_at은 close_at 전이어야 한다. 발표된 회차는 불가. STAFF. FR-MT-01")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_INVALID_STATUS")
    @PatchMapping("/{roundId}/times")
    ApiResponse<AdminMatchRoundResponse> updateTimes(
            @PathVariable Long roundId,
            @Valid @RequestBody UpdateRoundTimesRequest request
    );

    @Operation(summary = "회차 수동 오픈", description = "SCHEDULED → OPEN. 스케줄러 대체·복구용. STAFF. FR-MT-02")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_INVALID_STATUS")
    @PostMapping("/{roundId}/open")
    ApiResponse<AdminMatchRoundResponse> open(@PathVariable Long roundId);

    @Operation(summary = "회차 마감 + 배치 실행", description = "OPEN → CLOSED 전이 후 매칭 배치를 실행하고 결과를 저장한다. 스케줄러 실패 시 수동 실행 경로(NFR-AV-03). STAFF. FR-MT-02")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_INVALID_STATUS")
    @PostMapping("/{roundId}/close")
    ApiResponse<RoundBatchResultResponse> close(@PathVariable Long roundId);

    @Operation(summary = "배치 재실행", description = "CLOSED이고 발표 전인 회차의 결과를 지우고 다시 배치한다. STAFF. FR-MT-24")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_INVALID_STATUS")
    @PostMapping("/{roundId}/rerun")
    ApiResponse<RoundBatchResultResponse> rerun(@PathVariable Long roundId);

    @Operation(summary = "배치 결과 확인", description = "풀 인원·성비·매칭/미매칭 인원·쌍 수·평균 점수. 발표 전 확인용. STAFF. FR-MT-24")
    @GetMapping("/{roundId}/result")
    ApiResponse<RoundBatchResultResponse> getResult(@PathVariable Long roundId);

    @Operation(summary = "회차 발표", description = "CLOSED → PUBLISHED. 미매칭 신청을 다음 회차로 이월(CARRIED)하고 다음 회차를 연다. OWNER만. FR-MT-03·04")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "MATCH_ROUND_INVALID_STATUS")
    @PostMapping("/{roundId}/publish")
    ApiResponse<AdminMatchRoundResponse> publish(@PathVariable Long roundId);
}
