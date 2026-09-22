package com.yufesta.domain.match.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.controller.api.AdminMatchApi;
import com.yufesta.domain.match.dto.request.UpdateRoundTimesRequest;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.service.MatchRoundAdminService;
import com.yufesta.domain.match.service.MatchRoundBatchService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인스타팅 운영자 API. 명세는 AdminMatchApi
 */
@RestController
public class AdminMatchController implements AdminMatchApi {

    private final MatchRoundAdminService matchRoundAdminService;
    private final MatchRoundBatchService matchRoundBatchService;

    public AdminMatchController(
            MatchRoundAdminService matchRoundAdminService,
            MatchRoundBatchService matchRoundBatchService
    ) {
        this.matchRoundAdminService = matchRoundAdminService;
        this.matchRoundBatchService = matchRoundBatchService;
    }

    @Override
    public ApiResponse<List<AdminMatchRoundResponse>> getRounds() {
        return ApiResponse.success(matchRoundAdminService.getRounds());
    }

    @Override
    public ApiResponse<AdminMatchRoundResponse> updateTimes(Long roundId, UpdateRoundTimesRequest request) {
        return ApiResponse.success(matchRoundAdminService.updateTimes(roundId, request));
    }

    @Override
    public ApiResponse<AdminMatchRoundResponse> open(Long roundId) {
        return ApiResponse.success(matchRoundAdminService.open(roundId));
    }

    @Override
    public ApiResponse<RoundBatchResultResponse> close(Long roundId) {
        return ApiResponse.success(matchRoundBatchService.close(roundId));
    }

    @Override
    public ApiResponse<RoundBatchResultResponse> rerun(Long roundId) {
        return ApiResponse.success(matchRoundBatchService.rerun(roundId));
    }

    @Override
    public ApiResponse<RoundBatchResultResponse> getResult(Long roundId) {
        return ApiResponse.success(matchRoundAdminService.getResult(roundId));
    }

    @Override
    public ApiResponse<AdminMatchRoundResponse> publish(Long roundId) {
        return ApiResponse.success(matchRoundBatchService.publish(roundId));
    }
}
