package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.MatchRound;
import lombok.Builder;

/**
 * 홈 요약(FR-MT-50)에서 <b>모든 사용자에게 같은 부분</b>. 서비스 계층 캐시에 담기는 값이다.
 *
 * <p>공개 GET은 보통 {@code CachedResponseFilter}가 URL을 키로 응답 전체를 캐시한다. 요약은 그럴 수 없다.
 * 응답에 요청마다 달라지는 {@code serverNow}와 로그인 사용자별 {@code my}가 섞여 있어, 응답 전체를 키 하나에
 * 담으면 남의 신청 상태가 그대로 노출된다. 그래서 갈라지지 않는 이 부분만 따로 캐시하고
 * 나머지는 요청마다 계산한다.
 *
 * <p>Swagger에 나가지 않는다. {@link MatchSummaryResponse}를 조립하는 재료일 뿐이고 이 형태로 응답되지 않는다.
 */
@Builder
public record MatchSummaryCommonResponse(
        MatchRoundResponse currentRound,
        MatchRoundResponse nextRound,
        long applicantCount
) {

    public static MatchSummaryCommonResponse of(MatchRound currentRound, MatchRound nextRound, long applicantCount) {
        return MatchSummaryCommonResponse.builder()
                .currentRound(MatchRoundResponse.from(currentRound))
                .nextRound(nextRound == null ? null : MatchRoundResponse.from(nextRound))
                .applicantCount(applicantCount)
                .build();
    }
}
