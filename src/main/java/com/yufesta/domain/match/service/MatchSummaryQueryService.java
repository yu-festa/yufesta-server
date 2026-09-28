package com.yufesta.domain.match.service;

import com.yufesta.domain.match.dto.response.LastResultResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryCommonResponse;
import com.yufesta.domain.match.dto.response.MySummaryResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 요약의 DB 조회 부분. 캐시를 확인한 뒤에만 불리도록 {@link MatchSummaryService}와 빈을 나눴다.
 *
 * <p>한 클래스에 두면 캐시 확인까지 트랜잭션 안에 들어간다. 읽기 전용 트랜잭션은 SELECT를 하나도 보내지 않아도
 * 시작·종료만으로 풀에서 커넥션을 빌리고 제어문 5개({@code SET SESSION TRANSACTION READ ONLY},
 * {@code SET autocommit=0}, {@code COMMIT}, {@code SET autocommit=1}, {@code SET SESSION TRANSACTION READ WRITE})를
 * DB로 보낸다(2026-09-28 로컬 MySQL general log로 확인). 캐시가 맞은 요청이 그 비용을 내지 않게
 * 트랜잭션 경계를 이 클래스의 public 메서드로 옮겼다.
 */
@Service
@Transactional(readOnly = true)
public class MatchSummaryQueryService {

    private final MatchRoundService matchRoundService;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;

    public MatchSummaryQueryService(
            MatchRoundService matchRoundService,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository
    ) {
        this.matchRoundService = matchRoundService;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
    }

    /**
     * 모든 사용자에게 같은 부분(현재·다음 회차, 신청자 수)을 DB에서 읽는다. 캐시의 원본 계산이다.
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MatchSummaryCommonResponse loadCommon() {
        MatchRound current = matchRoundService.getCurrentRound();
        MatchRound next = matchRoundService.findNextRound(current).orElse(null);
        long applicantCount = applicationRepository.countByRound_IdAndCanceledAtIsNull(current.getId());
        return MatchSummaryCommonResponse.of(current, next, applicantCount);
    }

    /**
     * 로그인 사용자의 상태를 읽는다. 사용자마다 달라 캐시하지 않는다.
     * <p>결과(lastResult)는 published_at이 있는 회차에서만 계산한다(FR-MT-04).
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MySummaryResponse loadMy(Long userId) {
        MatchRound current = matchRoundService.getCurrentRound();
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
