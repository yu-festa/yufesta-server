package com.yufesta.domain.match.service;

import com.yufesta.domain.match.dto.response.LastResultResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MySummaryResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 인스타팅 블록 조회(FR-MT-50~56)
 */
@Service
@Transactional(readOnly = true)
public class MatchSummaryService {

    private final MatchRoundService matchRoundService;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;
    private final Clock clock;

    public MatchSummaryService(
            MatchRoundService matchRoundService,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository,
            Clock clock
    ) {
        this.matchRoundService = matchRoundService;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
        this.clock = clock;
    }

    /**
     * 홈 블록 데이터를 한 번에 반환한다. 비로그인(userId null)이면 my는 null.
     * <p>결과(lastResult)는 published_at이 있는 회차에서만 계산한다(FR-MT-04).
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MatchSummaryResponse getSummary(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        MatchRound current = matchRoundService.getCurrentRound();
        MatchRound next = matchRoundService.findNextRound(current).orElse(null);
        long applicantCount = applicationRepository.countByRound_IdAndCanceledAtIsNull(current.getId());
        MySummaryResponse my = userId == null ? null : mySummary(userId, current);
        return MatchSummaryResponse.of(now, current, next, applicantCount, my);
    }

    private MySummaryResponse mySummary(Long userId, MatchRound current) {
        boolean applied = findActiveApplication(userId, current).isPresent();
        LastResultResponse lastResult = matchRoundService.findLatestPublishedRound()
                .flatMap(published -> findActiveApplication(userId, published)
                        .map(application -> LastResultResponse.of(published.getSeq(), resultStatus(application))))
                .orElse(null);
        return MySummaryResponse.of(applied, lastResult);
    }

    private Optional<Application> findActiveApplication(Long userId, MatchRound round) {
        return applicationRepository.findByUser_IdAndRound_Id(userId, round.getId())
                .filter(application -> !application.isCanceled());
    }

    // 방향성 2행 저장이라 내 신청 id로 한 행이라도 있으면 매칭된 것
    private MatchResultStatus resultStatus(Application application) {
        return matchRepository.existsByApplication_Id(application.getId())
                ? MatchResultStatus.MATCHED
                : MatchResultStatus.UNMATCHED;
    }
}
