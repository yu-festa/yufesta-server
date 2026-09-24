package com.yufesta.domain.club.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.club.dto.response.ClubResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 라인업 공개 API 명세
 */
@Tag(name = "Club", description = "라인업·동아리 홍보 (FR-LU)")
@RequestMapping("/api/v1/clubs")
public interface ClubApi {

    @Operation(
            summary = "라인업 목록",
            description = "동아리 카드 전체. 표시 순서·이름순이며 카드마다 공연 시간·무대(performances)가 붙는다. "
                    + "홈 '오늘의 라인업' 캐러셀도 이 목록을 쓴다. 로그인 불필요. FR-LU-01~03"
    )
    @GetMapping
    ApiResponse<List<ClubResponse>> getClubs();

    @Operation(summary = "동아리 카드", description = "동아리 하나의 카드. 로그인 불필요. FR-LU-01")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CLUB_NOT_FOUND")
    @GetMapping("/{clubId}")
    ApiResponse<ClubResponse> getClub(@PathVariable Long clubId);
}
