package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.request.UpdateRoundTimesRequest;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 회차 관리: 목록, 시각 수정, 수동 오픈, 결과 확인(FR-MT-01·24). 배치·발표는 MatchRoundBatchService
 */
@Service
@Transactional(readOnly = true)
public class MatchRoundAdminService {

    private static final Duration CLOSE_BEFORE_PUBLISH = Duration.ofMinutes(10);

    private final MatchRoundRepository matchRoundRepository;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;

    public MatchRoundAdminService(
            MatchRoundRepository matchRoundRepository,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository
    ) {
        this.matchRoundRepository = matchRoundRepository;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
    }

    /** 회차 전체를 seq 순으로 반환한다. */
    public List<AdminMatchRoundResponse> getRounds() {
        return matchRoundRepository.findAllByOrderBySeqAsc().stream()
                .map(AdminMatchRoundResponse::from)
                .toList();
    }

    /**
     * 회차를 수동으로 연다(SCHEDULED → OPEN). 스케줄러 대체·복구용.
     * @throws CustomException MATCH_ROUND_NOT_FOUND, MATCH_ROUND_INVALID_STATUS
     */
    @Transactional
    public AdminMatchRoundResponse open(Long roundId) {
        MatchRound round = lockRound(roundId);
        round.open();
        return AdminMatchRoundResponse.from(round);
    }

    /**
     * 회차 시각을 수정한다(FR-MT-01). 마감은 발표 10분 전, 시작은 마감 전이어야 한다. 발표된 회차는 수정 불가.
     * @throws CustomException MATCH_ROUND_NOT_FOUND, MATCH_ROUND_INVALID_STATUS, INVALID_INPUT_VALUE
     */
    @Transactional
    public AdminMatchRoundResponse updateTimes(Long roundId, UpdateRoundTimesRequest request) {
        MatchRound round = lockRound(roundId);
        if (round.getStatus() == RoundStatus.PUBLISHED) {
            throw new CustomException(ErrorCode.MATCH_ROUND_INVALID_STATUS);
        }
        requireValidTimes(request.openAt(), request.closeAt(), request.publishAt());
        round.updateTimes(request.openAt(), request.closeAt(), request.publishAt());
        return AdminMatchRoundResponse.from(round);
    }

    /**
     * 배치 결과 요약을 반환한다(FR-MT-24). 미실행이면 풀 인원만 채워진다.
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public RoundBatchResultResponse getResult(Long roundId) {
        MatchRound round = matchRoundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_ROUND_NOT_FOUND));
        return RoundBatchResultResponse.of(
                round,
                applicationRepository.findPoolByRoundId(roundId),
                matchRepository.findAllByRound_Id(roundId)
        );
    }

    private MatchRound lockRound(Long roundId) {
        return matchRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_ROUND_NOT_FOUND));
    }

    // close_at = publish_at − 10분(FR-MT-02), open_at < close_at
    private static void requireValidTimes(LocalDateTime openAt, LocalDateTime closeAt, LocalDateTime publishAt) {
        boolean closeIsTenMinutesBefore = closeAt.equals(publishAt.minus(CLOSE_BEFORE_PUBLISH));
        if (!closeIsTenMinutesBefore || !openAt.isBefore(closeAt)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
