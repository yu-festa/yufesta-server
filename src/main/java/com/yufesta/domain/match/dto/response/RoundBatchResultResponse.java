package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.RoundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * 배치 결과 요약. 발표 전 운영자 확인용(FR-MT-24). 개인 식별 정보는 없다
 */
@Builder
public record RoundBatchResultResponse(
        @Schema(description = "회차 번호", example = "1") int roundSeq,
        RoundStatus status,
        LocalDateTime executedAt,
        LocalDateTime publishedAt,
        @Schema(description = "배치 풀 인원(취소·차단 제외)", example = "80") int poolSize,
        @Schema(description = "풀 남성 수", example = "50") int poolMen,
        @Schema(description = "풀 여성 수", example = "30") int poolWomen,
        @Schema(description = "1명 이상 매칭된 인원", example = "80") int matchedApplicants,
        @Schema(description = "미매칭 인원", example = "0") int unmatchedApplicants,
        @Schema(description = "매칭 쌍 수", example = "50") int pairCount,
        @Schema(description = "1차 배정 쌍 수", example = "30") int firstPassPairs,
        @Schema(description = "2차 배정 쌍 수", example = "20") int secondPassPairs,
        @Schema(description = "쌍 평균 점수. 쌍이 없으면 null", example = "2.35") BigDecimal averageScore
) {

    // matches는 쌍당 2행이므로 행 수를 2로 나눈 것이 쌍 수다
    public static RoundBatchResultResponse of(MatchRound round, List<Application> pool, List<Match> matchRows) {
        int men = (int) pool.stream().filter(a -> a.getGender() == Gender.M).count();
        int matched = (int) matchRows.stream().map(m -> m.getApplication().getId()).distinct().count();
        int firstPassRows = (int) matchRows.stream().filter(m -> m.getAssignPass() == 1).count();
        BigDecimal average = matchRows.isEmpty() ? null : matchRows.stream()
                .map(Match::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(matchRows.size()), 2, RoundingMode.HALF_UP);

        return RoundBatchResultResponse.builder()
                .roundSeq(round.getSeq())
                .status(round.getStatus())
                .executedAt(round.getExecutedAt())
                .publishedAt(round.getPublishedAt())
                .poolSize(pool.size())
                .poolMen(men)
                .poolWomen(pool.size() - men)
                .matchedApplicants(matched)
                .unmatchedApplicants(pool.size() - matched)
                .pairCount(matchRows.size() / 2)
                .firstPassPairs(firstPassRows / 2)
                .secondPassPairs((matchRows.size() - firstPassRows) / 2)
                .averageScore(average)
                .build();
    }
}
