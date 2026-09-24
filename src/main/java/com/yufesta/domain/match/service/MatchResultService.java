package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.response.MatchResultResponse;
import com.yufesta.domain.match.dto.response.PartnerCardResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.MatchResultStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발표 후 내 매칭 결과 조회(FR-MT-31·32). 발표 전 노출 금지(FR-MT-04)와 신고 상대 제외(FR-MT-41)를 여기서 지킨다
 */
@Service
@Transactional(readOnly = true)
public class MatchResultService {

    private final MatchRoundService matchRoundService;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final Clock clock;

    public MatchResultService(
            MatchRoundService matchRoundService,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository,
            BlockRepository blockRepository,
            Clock clock
    ) {
        this.matchRoundService = matchRoundService;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
        this.blockRepository = blockRepository;
        this.clock = clock;
    }

    /**
     * 내 결과를 반환한다. roundSeq가 없으면 가장 최근 발표된 회차.
     * <p>발표 전 회차는 matches가 있어도 409. 카드는 점수 내림차순이며 내가 신고한 상대는 제외한다.
     * @throws CustomException UNAUTHORIZED, MATCH_ROUND_NOT_FOUND, MATCH_RESULT_NOT_PUBLISHED, APPLICATION_NOT_FOUND
     */
    public MatchResultResponse getMyResult(Long userId, Integer roundSeq) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        MatchRound round = resolvePublishedRound(roundSeq);
        Application mine = applicationRepository.findByUser_IdAndRound_Id(userId, round.getId())
                .filter(application -> !application.isCanceled())
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));

        List<Match> matches = matchRepository.findAllByApplicationId(mine.getId());
        Set<Long> reportedUserIds = blockRepository.findAllByReporter_Id(userId).stream()
                .map(block -> block.getTarget().getId())
                .collect(Collectors.toSet());
        List<PartnerCardResponse> partners = matches.stream()
                .filter(match -> !reportedUserIds.contains(match.getPartnerApplication().getUser().getId()))
                .map(match -> PartnerCardResponse.of(match, mine))
                .toList();

        // 매칭 여부는 신고로 카드가 빠져도 바뀌지 않는다. 미매칭자만 이월 대상이기 때문
        MatchResultStatus status = matches.isEmpty() ? MatchResultStatus.UNMATCHED : MatchResultStatus.MATCHED;
        Optional<MatchRound> next = matchRoundService.findNextRound(round);
        boolean hasNextApplication = next
                .flatMap(nextRound -> applicationRepository.findByUser_IdAndRound_Id(userId, nextRound.getId()))
                .filter(application -> !application.isCanceled())
                .isPresent();
        boolean nextAccepting = next.map(nextRound -> nextRound.isAcceptingAt(LocalDateTime.now(clock))).orElse(false);

        return MatchResultResponse.builder()
                .roundSeq(round.getSeq())
                .status(status)
                .partners(partners)
                .nextRoundSeq(next.map(MatchRound::getSeq).orElse(null))
                .hasNextRoundApplication(hasNextApplication)
                .canRejoin(status == MatchResultStatus.MATCHED && nextAccepting && !hasNextApplication)
                .build();
    }

    // 지정 회차는 발표됐는지 확인하고, 미지정이면 최근 발표 회차. 둘 다 없으면 "발표 전"
    private MatchRound resolvePublishedRound(Integer roundSeq) {
        if (roundSeq == null) {
            return matchRoundService.findLatestPublishedRound()
                    .orElseThrow(() -> new CustomException(ErrorCode.MATCH_RESULT_NOT_PUBLISHED));
        }
        MatchRound round = matchRoundService.getRoundBySeq(roundSeq);
        if (!round.isPublished()) {
            throw new CustomException(ErrorCode.MATCH_RESULT_NOT_PUBLISHED);
        }
        return round;
    }
}
