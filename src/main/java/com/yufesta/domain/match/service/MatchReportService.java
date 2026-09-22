package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.match.dto.request.ReportMatchRequest;
import com.yufesta.domain.match.dto.request.ReviewMatchReportRequest;
import com.yufesta.domain.match.dto.response.AdminMatchReportResponse;
import com.yufesta.domain.match.dto.response.MatchReportResponse;
import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.BlockDecision;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.TargetReportCount;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 상대 신고와 누적 제재, 운영자 검토(FR-MT-40~42, FR-ADM-03). 제재 판정은 applySanction 한 곳에서만 한다
 */
@Service
@Transactional(readOnly = true)
public class MatchReportService {

    private static final int MAX_PAGE_SIZE = 50;

    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final ApplicationRepository applicationRepository;
    private final MatchRoundService matchRoundService;
    private final UserService userService;
    private final AppSettingReader appSettingReader;
    private final Clock clock;

    public MatchReportService(
            MatchRepository matchRepository,
            BlockRepository blockRepository,
            ApplicationRepository applicationRepository,
            MatchRoundService matchRoundService,
            UserService userService,
            AppSettingReader appSettingReader,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.blockRepository = blockRepository;
        this.applicationRepository = applicationRepository;
        this.matchRoundService = matchRoundService;
        this.userService = userService;
        this.appSettingReader = appSettingReader;
        this.clock = clock;
    }

    /**
     * 매칭 상대를 신고한다. 저장 직후 대상의 누적 신고를 집계해 제재를 판정한다.
     * <p>검증: 내 결과의 매치인지(아니면 존재를 숨기고 404), 회차 발표 후(FR-MT-04), 같은 상대 재신고 아님(uk_blocks_pair).
     * 신고 즉시 내 결과에서 카드가 빠지고 재매칭에서 제외된다(FR-MT-41). 대상에게는 아무것도 알리지 않는다.
     * <p>READ_COMMITTED: 대상 회원 행을 잠근 뒤 세는 누적 수에 방금 커밋된 다른 신고가 보여야 한다.
     * 기본 REPEATABLE READ에서는 잠금 전에 잡힌 스냅샷 때문에 그 신고가 빠진다(§5).
     * @throws CustomException UNAUTHORIZED, MATCH_NOT_FOUND, MATCH_RESULT_NOT_PUBLISHED, USER_NOT_FOUND, BLOCK_ALREADY_EXISTS
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MatchReportResponse report(Long userId, ReportMatchRequest request) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        Match match = matchRepository.findByIdWithParticipants(request.matchId())
                .filter(found -> found.getApplication().getUser().getId().equals(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_NOT_FOUND));
        if (!match.getRound().isPublished()) {
            throw new CustomException(ErrorCode.MATCH_RESULT_NOT_PUBLISHED);
        }

        // 대상 행을 먼저 잠가 같은 대상에 대한 신고 처리를 한 줄로 세운다. 잠금보다 먼저 저장하면 서로의 신고를 못 보고 임계를 지나친다(§5)
        User target = userService.getUserForUpdate(match.getPartnerApplication().getUser().getId());
        if (blockRepository.existsByReporter_IdAndTarget_Id(userId, target.getId())) {
            throw new CustomException(ErrorCode.BLOCK_ALREADY_EXISTS);
        }

        Block block = Block.builder()
                .reporter(match.getApplication().getUser())
                .target(target)
                .round(match.getRound())
                .reason(request.reason())
                .detail(normalizeDetail(request.detail()))
                .build();
        Block saved = saveAndTranslate(block);
        applySanction(target, LocalDateTime.now(clock));
        return MatchReportResponse.of(saved, match.getId());
    }

    /**
     * 운영자 검토 목록. 최신순 오프셋 페이지(운영자 화면 전용, §9).
     * reviewed가 null이면 전체, false면 미검토, true면 검토 완료. 대상별 유효 신고 수는 group by 한 번으로 붙인다.
     * @throws CustomException INVALID_INPUT_VALUE(page 음수, size 1~50 밖)
     */
    public List<AdminMatchReportResponse> getReports(Boolean reviewed, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<Block> blocks = findPage(reviewed, PageRequest.of(page, size));
        Map<Long, Long> counts = countValidReportsByTarget(blocks);
        return blocks.stream()
                .map(block -> AdminMatchReportResponse.of(block, counts.getOrDefault(block.getTarget().getId(), 0L)))
                .toList();
    }

    /**
     * 신고를 검토한다(FR-MT-42). CONFIRM은 임계 미달이어도 대상을 즉시 제재하고, DISMISS는 집계에서 빼며
     * 그 결과 임계 아래로 내려가면 제재를 푼다. 재검토할 수 있고 마지막 결정이 유효하다. 삭제된 신청은 되살리지 않는다.
     * <p>READ_COMMITTED 이유는 report와 같다.
     * @throws CustomException BLOCK_NOT_FOUND, USER_NOT_FOUND
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminMatchReportResponse review(Long blockId, ReviewMatchReportRequest request) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new CustomException(ErrorCode.BLOCK_NOT_FOUND));
        User target = userService.getUserForUpdate(block.getTarget().getId());
        LocalDateTime now = LocalDateTime.now(clock);
        block.review(request.decision(), now);
        // 집계 쿼리가 방금 바꾼 결정을 보도록 먼저 반영한다
        blockRepository.saveAndFlush(block);
        applySanction(target, now);
        return AdminMatchReportResponse.of(block, blockRepository.countValidReports(target.getId(), BlockDecision.DISMISS));
    }

    // 신고 접수와 운영자 검토가 같은 규칙을 탄다: CONFIRM이 하나라도 있거나 유효 신고 수(DISMISS 제외)가 임계 이상이면 차단,
    // 아니면 해제. 상태를 "계산 결과"로 맞추므로 어떤 순서로 몇 번 불려도 결과가 같다. 호출 전에 대상 행이 잠겨 있어야 한다
    private void applySanction(User target, LocalDateTime now) {
        boolean shouldBlock = blockRepository.existsByTarget_IdAndDecision(target.getId(), BlockDecision.CONFIRM)
                || blockRepository.countValidReports(target.getId(), BlockDecision.DISMISS)
                >= appSettingReader.getInt(SettingKey.REPORT_BLOCK_THRESHOLD);
        if (shouldBlock && !target.isMatchingBlocked()) {
            target.blockMatching(now);
            removeFromCurrentPool(target.getId());
        } else if (!shouldBlock && target.isMatchingBlocked()) {
            target.unblockMatching();
        }
    }

    // 현재 회차가 아직 배치 전(SCHEDULED·OPEN)이면 신청을 물리 삭제해 풀에서 뺀다(FR-MT-41, §5 삭제 정책).
    // 배치가 끝난 CLOSED는 matches가 그 신청을 참조하므로 두고, 재실행·이월·다음 회차 풀 조건(matching_blocked_at is null)이 걸러낸다
    private void removeFromCurrentPool(Long targetId) {
        MatchRound current = matchRoundService.getCurrentRound();
        if (current.getStatus() != RoundStatus.SCHEDULED && current.getStatus() != RoundStatus.OPEN) {
            return;
        }
        applicationRepository.findByUser_IdAndRound_Id(targetId, current.getId())
                .ifPresent(applicationRepository::delete);
    }

    private List<Block> findPage(Boolean reviewed, Pageable pageable) {
        if (reviewed == null) {
            return blockRepository.findAllByOrderByIdDesc(pageable);
        }
        return reviewed
                ? blockRepository.findAllByReviewedAtIsNotNullOrderByIdDesc(pageable)
                : blockRepository.findAllByReviewedAtIsNullOrderByIdDesc(pageable);
    }

    private Map<Long, Long> countValidReportsByTarget(List<Block> blocks) {
        Set<Long> targetIds = blocks.stream()
                .map(block -> block.getTarget().getId())
                .collect(Collectors.toSet());
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return blockRepository.countValidReportsByTargetIds(targetIds, BlockDecision.DISMISS).stream()
                .collect(Collectors.toMap(TargetReportCount::targetUserId, TargetReportCount::count));
    }

    private static String normalizeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return detail.trim();
    }

    // 이중 클릭으로 선검사를 통과해도 uk_blocks_pair가 잡으면 같은 409로(§5). blocks의 유니크는 이 쌍 하나뿐이다
    private Block saveAndTranslate(Block block) {
        try {
            return blockRepository.saveAndFlush(block);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.BLOCK_ALREADY_EXISTS);
        }
    }
}
