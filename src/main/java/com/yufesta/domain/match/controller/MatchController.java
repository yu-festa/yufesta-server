package com.yufesta.domain.match.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.controller.api.MatchApi;
import com.yufesta.domain.match.dto.request.ApplyMatchRequest;
import com.yufesta.domain.match.dto.request.UpdateApplicationRequest;
import com.yufesta.domain.match.dto.response.ApplicationResponse;
import com.yufesta.domain.match.dto.response.MatchResultResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MatchTagResponse;
import com.yufesta.domain.match.service.ApplicationService;
import com.yufesta.domain.match.service.MatchResultService;
import com.yufesta.domain.match.service.MatchSummaryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인스타팅 공개 API. 명세는 MatchApi
 */
@RestController
public class MatchController implements MatchApi {

    private final MatchSummaryService matchSummaryService;
    private final ApplicationService applicationService;
    private final MatchResultService matchResultService;

    public MatchController(
            MatchSummaryService matchSummaryService,
            ApplicationService applicationService,
            MatchResultService matchResultService
    ) {
        this.matchSummaryService = matchSummaryService;
        this.applicationService = applicationService;
        this.matchResultService = matchResultService;
    }

    @Override
    public ApiResponse<MatchSummaryResponse> getSummary(Long userId) {
        return ApiResponse.success(matchSummaryService.getSummary(userId));
    }

    @Override
    public ApiResponse<List<MatchTagResponse>> getTags() {
        return ApiResponse.success(MatchTagResponse.all());
    }

    @Override
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(Long userId, ApplyMatchRequest request) {
        ApplicationResponse response = applicationService.apply(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "신청이 완료되었어요.", response));
    }

    @Override
    public ApiResponse<ApplicationResponse> getMyApplication(Long userId) {
        return ApiResponse.success(applicationService.getMine(userId));
    }

    @Override
    public ApiResponse<ApplicationResponse> updateMyApplication(Long userId, UpdateApplicationRequest request) {
        return ApiResponse.success(applicationService.update(userId, request));
    }

    @Override
    public void cancelMyApplication(Long userId) {
        applicationService.cancel(userId);
    }

    @Override
    public ResponseEntity<ApiResponse<ApplicationResponse>> rejoin(Long userId) {
        ApplicationResponse response = applicationService.rejoin(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "2회차 참여가 완료되었어요.", response));
    }

    @Override
    public ApiResponse<MatchResultResponse> getMyResult(Long userId, Integer roundSeq) {
        return ApiResponse.success(matchResultService.getMyResult(userId, roundSeq));
    }
}
