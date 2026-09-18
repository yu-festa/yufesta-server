package com.yufesta.domain.match.controller.api;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MatchTagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}
