package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.MatchRound;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 홈 인스타팅 블록(FR-MT-50). 한 번의 호출로 서버 시각·회차·신청자 수·내 상태를 준다
 */
@Builder
public record MatchSummaryResponse(
        @Schema(description = "서버 기준 현재 시각(KST). 카운트다운의 기준", example = "2026-10-02T15:30:00") LocalDateTime serverNow,
        @Schema(description = "현재 회차. 전부 발표됐으면 마지막 회차") MatchRoundResponse currentRound,
        @Schema(description = "다음 회차. 마지막 회차면 null") MatchRoundResponse nextRound,
        @Schema(description = "현재 회차 신청자 수(취소 제외)", example = "137") long applicantCount,
        @Schema(description = "로그인 사용자의 상태. 비로그인이면 null") MySummaryResponse my
) {

    public static MatchSummaryResponse of(
            LocalDateTime serverNow,
            MatchRound currentRound,
            MatchRound nextRound,
            long applicantCount,
            MySummaryResponse my
    ) {
        return MatchSummaryResponse.builder()
                .serverNow(serverNow)
                .currentRound(MatchRoundResponse.from(currentRound))
                .nextRound(nextRound == null ? null : MatchRoundResponse.from(nextRound))
                .applicantCount(applicantCount)
                .my(my)
                .build();
    }
}
