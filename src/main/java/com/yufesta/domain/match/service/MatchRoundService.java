package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 조회. "현재 회차"의 정의를 한 곳에 두어 홈·신청·배치·스케줄러가 같은 기준을 쓴다
 */
@Service
@Transactional(readOnly = true)
public class MatchRoundService {

    private final MatchRoundRepository matchRoundRepository;

    public MatchRoundService(MatchRoundRepository matchRoundRepository) {
        this.matchRoundRepository = matchRoundRepository;
    }

    /**
     * 현재 회차를 반환한다. seq 순으로 아직 발표되지 않은 첫 회차이며, 전부 발표됐으면 마지막 회차(종료 표시용).
     * 마감~발표 사이(CLOSED)도 현재 회차로 본다(FR-MT-02).
     * @throws CustomException MATCH_ROUND_NOT_FOUND(회차 행이 하나도 없음)
     */
    public MatchRound getCurrentRound() {
        List<MatchRound> rounds = matchRoundRepository.findAllByOrderBySeqAsc();
        if (rounds.isEmpty()) {
            throw new CustomException(ErrorCode.MATCH_ROUND_NOT_FOUND);
        }
        return rounds.stream()
                .filter(round -> round.getStatus() != RoundStatus.PUBLISHED)
                .findFirst()
                .orElse(rounds.get(rounds.size() - 1));
    }

    /** 주어진 회차의 다음 회차. 마지막 회차면 비어 있다. */
    public Optional<MatchRound> findNextRound(MatchRound round) {
        return matchRoundRepository.findBySeq(round.getSeq() + 1);
    }

    /** 가장 최근에 발표된 회차. 아직 발표된 회차가 없으면 비어 있다. */
    public Optional<MatchRound> findLatestPublishedRound() {
        return matchRoundRepository.findFirstByPublishedAtIsNotNullOrderBySeqDesc();
    }

    /**
     * 회차 번호로 회차를 조회한다.
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MatchRound getRoundBySeq(int seq) {
        return matchRoundRepository.findBySeq(seq)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_ROUND_NOT_FOUND));
    }

    /**
     * id로 회차를 조회한다.
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MatchRound getRound(Long roundId) {
        return matchRoundRepository.findById(roundId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_ROUND_NOT_FOUND));
    }
}
