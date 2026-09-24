package com.yufesta.domain.club.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.club.controller.api.ClubApi;
import com.yufesta.domain.club.dto.response.ClubResponse;
import com.yufesta.domain.club.service.ClubService;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

/**
 * 라인업 공개 API. 명세는 ClubApi
 */
@RestController
public class ClubController implements ClubApi {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @Override
    public ApiResponse<List<ClubResponse>> getClubs() {
        return ApiResponse.success(clubService.getClubs());
    }

    @Override
    public ApiResponse<ClubResponse> getClub(Long clubId) {
        return ApiResponse.success(clubService.getClub(clubId));
    }
}
