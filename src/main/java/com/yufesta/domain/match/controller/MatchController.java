package com.yufesta.domain.match.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.controller.api.MatchApi;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MatchTagResponse;
import com.yufesta.domain.match.service.MatchSummaryService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인스타팅 공개 API. 명세는 MatchApi
 */
@RestController
public class MatchController implements MatchApi {

    private final MatchSummaryService matchSummaryService;

    public MatchController(MatchSummaryService matchSummaryService) {
        this.matchSummaryService = matchSummaryService;
    }

    @Override
    public ApiResponse<MatchSummaryResponse> getSummary(Long userId) {
        return ApiResponse.success(matchSummaryService.getSummary(userId));
    }

    @Override
    public ApiResponse<List<MatchTagResponse>> getTags() {
        return ApiResponse.success(MatchTagResponse.all());
    }
}
