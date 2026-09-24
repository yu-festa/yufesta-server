package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.match.dto.response.AdminMatchRoundResponse;
import com.yufesta.domain.match.dto.response.RoundBatchResultResponse;
import com.yufesta.domain.match.engine.Candidate;
import com.yufesta.domain.match.engine.MatchResult;
import com.yufesta.domain.match.engine.MatchWeights;
import com.yufesta.domain.match.engine.MatchingEngine;
import com.yufesta.domain.match.engine.UserPair;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.RoundStatus;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.BlockRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.match.repository.MatchRoundRepository;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 마감 배치·재배치·발표·이월(FR-MT-02~05, 23). 운영자 API와 스케줄러가 같은 메서드를 부른다.
 * 회차 행을 PESSIMISTIC_WRITE로 잠가 동시 실행을 막고, 상태 전이는 MatchRound가 검증한다
 */
@Service
@Transactional(readOnly = true)
public class MatchRoundBatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchRoundBatchService.class);

    private final MatchRoundRepository matchRoundRepository;
    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;
    private final BlockRepository blockRepository;
    private final AppSettingReader appSettingReader;
    private final Clock clock;
    private final MatchingEngine engine = new MatchingEngine();

    public MatchRoundBatchService(
            MatchRoundRepository matchRoundRepository,
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository,
            BlockRepository blockRepository,
            AppSettingReader appSettingReader,
            Clock clock
    ) {
        this.matchRoundRepository = matchRoundRepository;
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
        this.blockRepository = blockRepository;
        this.appSettingReader = appSettingReader;
        this.clock = clock;
    }

    /**
     * 회차를 마감하고 배치를 실행해 결과를 저장한다(FR-MT-02).
     * <p>OPEN → CLOSED 전이 후 풀 조회 → 제외 쌍 → 엔진 → matches 2행씩 저장 → executed_at.
     * @throws CustomException MATCH_ROUND_NOT_FOUND, MATCH_ROUND_INVALID_STATUS(OPEN이 아님)
     */
    @Transactional
    public RoundBatchResultResponse close(Long roundId) {
        MatchRound round = lockRound(roundId);
        round.close();
        return runBatch(round);
    }

    /**
     * 마감된 회차의 결과를 지우고 배치를 다시 실행한다(FR-MT-24 확인 후 재실행용). 발표 전에만 가능하다.
     * @throws CustomException MATCH_ROUND_NOT_FOUND, MATCH_ROUND_INVALID_STATUS(CLOSED가 아님)
     */
    @Transactional
    public RoundBatchResultResponse rerun(Long roundId) {
        MatchRound round = lockRound(roundId);
        if (round.getStatus() != RoundStatus.CLOSED) {
            throw new CustomException(ErrorCode.MATCH_ROUND_INVALID_STATUS);
        }
        matchRepository.deleteAllByRoundId(round.getId());
        return runBatch(round);
    }

    /**
     * 회차를 발표하고 미매칭 신청을 다음 회차로 이월하며 다음 회차를 연다(FR-MT-02~04).
     * <p>CLOSED → PUBLISHED 전이 + published_at. 이월 복사는 같은 트랜잭션이다. 마지막 회차면 이월 없음.
     * @throws CustomException MATCH_ROUND_NOT_FOUND, MATCH_ROUND_INVALID_STATUS(CLOSED가 아님)
     */
    @Transactional
    public AdminMatchRoundResponse publish(Long roundId) {
        MatchRound round = lockRound(roundId);
        round.publish(LocalDateTime.now(clock));

        matchRoundRepository.findBySeq(round.getSeq() + 1).ifPresent(next -> {
            int carried = carryOver(round, next);
            if (next.getStatus() == RoundStatus.SCHEDULED) {
                next.open();
            }
            log.info("회차 {} 발표: {}건 이월, 회차 {} {}", round.getSeq(), carried, next.getSeq(), next.getStatus());
        });
        return AdminMatchRoundResponse.from(round);
    }

    private MatchRound lockRound(Long roundId) {
        return matchRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_ROUND_NOT_FOUND));
    }

    // 풀 → 후보 변환 → 엔진 → 방향성 2행 저장. 회차 상태 전이는 호출부가 끝낸 뒤 들어온다
    private RoundBatchResultResponse runBatch(MatchRound round) {
        List<Application> pool = applicationRepository.findPoolByRoundId(round.getId());
        List<MatchResult> results = engine.match(
                pool.stream().map(application -> toCandidate(application, round)).toList(),
                excludedPairs(round),
                weights(),
                appSettingReader.getInt(SettingKey.MATCH_MAX_PARTNERS)
        );

        Map<Long, Application> byId = pool.stream().collect(Collectors.toMap(Application::getId, Function.identity()));
        List<Match> rows = new ArrayList<>(results.size() * 2);
        for (MatchResult result : results) {
            Application a = byId.get(result.applicationId());
            Application b = byId.get(result.partnerApplicationId());
            rows.add(matchRow(round, a, b, result));
            rows.add(matchRow(round, b, a, result));
        }
        List<Match> saved = matchRepository.saveAll(rows);
        round.markExecuted(LocalDateTime.now(clock));

        log.info("회차 {} 배치: 풀 {}명, {}쌍", round.getSeq(), pool.size(), results.size());
        return RoundBatchResultResponse.of(round, pool, saved);
    }

    // 제외 쌍 = 신고·차단 전부(방향 무관) + 이전 회차에 매칭된 회원 쌍
    private Set<UserPair> excludedPairs(MatchRound round) {
        Set<UserPair> excluded = new HashSet<>();
        blockRepository.findAllUserIdPairs()
                .forEach(pair -> excluded.add(UserPair.of(pair.userId(), pair.otherUserId())));
        matchRepository.findMatchedUserIdPairsBeforeSeq(round.getSeq())
                .forEach(pair -> excluded.add(UserPair.of(pair.userId(), pair.otherUserId())));
        return excluded;
    }

    private MatchWeights weights() {
        return MatchWeights.builder()
                .tag(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_TAG))
                .slot(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_SLOT))
                .ageSame(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_SAME))
                .ageAdjacent(appSettingReader.getDecimal(SettingKey.MATCH_WEIGHT_AGE_ADJACENT))
                .build();
    }

    // 보고 싶은 공연은 배치 시점에 회차 규칙(FR-MT-05)을 다시 검사해 통과한 것만 점수에 쓴다(타임테이블 변경 대비)
    private static Candidate toCandidate(Application application, MatchRound round) {
        TimetableSlot wantedSlot = application.wantedSlotFor(round);
        return Candidate.builder()
                .applicationId(application.getId())
                .userId(application.getUser().getId())
                .gender(application.getGender())
                .tags(application.getTags())
                .wantedSlotId(wantedSlot == null ? null : wantedSlot.getId())
                .ageBand(application.getAgeBand())
                .entryType(application.getEntryType())
                .build();
    }

    private static Match matchRow(MatchRound round, Application mine, Application partner, MatchResult result) {
        return Match.builder()
                .round(round)
                .application(mine)
                .partnerApplication(partner)
                .score(result.score())
                .assignPass(result.pass())
                .build();
    }

    // 미매칭 신청을 다음 회차 행으로 복사(FR-MT-03). 이미 다음 회차에 신청이 있으면 건너뛴다
    private int carryOver(MatchRound round, MatchRound next) {
        Set<Long> matchedIds = matchRepository.findMatchedApplicationIdsByRoundId(round.getId());
        List<Application> carried = applicationRepository.findPoolByRoundId(round.getId()).stream()
                .filter(application -> !matchedIds.contains(application.getId()))
                .filter(application -> !applicationRepository.existsByUser_IdAndRound_Id(
                        application.getUser().getId(), next.getId()))
                .map(application -> copyForNextRound(application, next))
                .toList();
        applicationRepository.saveAll(carried);
        return carried.size();
    }

    private static Application copyForNextRound(Application source, MatchRound next) {
        return Application.builder()
                .user(source.getUser())
                .round(next)
                .instagramId(source.getInstagramId())
                .nickname(source.getNickname())
                .gender(source.getGender())
                .ageBand(source.getAgeBand())
                .tags(source.getTags())
                .intro(source.getIntro())
                .wantedSlot(source.wantedSlotFor(next))
                .entryType(EntryType.CARRIED)
                .sourceApplication(source)
                .termsVersion(source.getTermsVersion())
                .privacyVersion(source.getPrivacyVersion())
                .ageConfirmed(source.isAgeConfirmed())
                .agreedAt(source.getAgreedAt())
                .build();
    }
}
