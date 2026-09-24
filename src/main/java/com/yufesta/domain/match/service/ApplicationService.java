package com.yufesta.domain.match.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.dto.request.ApplyMatchRequest;
import com.yufesta.domain.match.dto.request.UpdateApplicationRequest;
import com.yufesta.domain.match.dto.response.ApplicationResponse;
import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.match.repository.ApplicationRepository;
import com.yufesta.domain.match.repository.MatchRepository;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인스타팅 신청 등록·조회·수정·취소(FR-MT-10~14). 회차 규칙과 중복 방지는 여기서만 검증한다
 */
@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private static final Pattern INSTAGRAM_ID = Pattern.compile("^[a-z0-9._]{1,30}$");
    private static final String UK_USER_ROUND = "uk_app_user_round";
    private static final String UK_ROUND_INSTA = "uk_app_round_insta";

    private final ApplicationRepository applicationRepository;
    private final MatchRepository matchRepository;
    private final MatchRoundService matchRoundService;
    private final UserService userService;
    private final WantedSlotService wantedSlotService;
    private final Clock clock;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            MatchRepository matchRepository,
            MatchRoundService matchRoundService,
            UserService userService,
            WantedSlotService wantedSlotService,
            Clock clock
    ) {
        this.applicationRepository = applicationRepository;
        this.matchRepository = matchRepository;
        this.matchRoundService = matchRoundService;
        this.userService = userService;
        this.wantedSlotService = wantedSlotService;
        this.clock = clock;
    }

    /**
     * 현재 회차에 신청한다. 취소했던 신청이 있으면 새 행 대신 그 행을 되살린다.
     * <p>검증: 회차 접수 중(FR-MT-02), 매칭 차단 회원 아님(FR-MT-41), 동의 3종(FR-MT-11),
     * 회차당 1건·인스타 ID 회차 내 유니크(FR-MT-13), 보고 싶은 공연은 발표 이후 시작(FR-MT-05). 인스타 ID는 정규화 후 저장한다.
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND, USER_MATCHING_BLOCKED, MATCH_ROUND_NOT_OPEN,
     *         INVALID_INPUT_VALUE, APPLICATION_ALREADY_EXISTS, APPLICATION_INSTAGRAM_DUPLICATE,
     *         TIMETABLE_SLOT_NOT_FOUND, APPLICATION_SLOT_NOT_SELECTABLE
     */
    @Transactional
    public ApplicationResponse apply(Long userId, ApplyMatchRequest request) {
        User user = requireUser(userId);
        requireNotBlocked(user);
        LocalDateTime now = LocalDateTime.now(clock);
        MatchRound round = requireAcceptingRound(now);
        requireAgreement(request);
        String instagramId = normalizeInstagramId(request.instagramId());

        Optional<Application> mine = applicationRepository.findByUser_IdAndRound_Id(userId, round.getId());
        if (mine.filter(application -> !application.isCanceled()).isPresent()) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        requireInstagramAvailable(round, instagramId, mine.orElse(null));
        TimetableSlot wantedSlot = wantedSlotService.requireSelectable(round, request.wantedSlotId());

        Application application = mine
                .map(canceled -> reapply(canceled, request, instagramId, wantedSlot, now))
                .orElseGet(() -> newApplication(user, round, request, instagramId, wantedSlot, now));
        return ApplicationResponse.from(saveAndTranslate(application));
    }

    /**
     * 현재 회차의 내 신청을 반환한다. 취소된 신청은 없는 것으로 본다.
     * @throws CustomException UNAUTHORIZED, MATCH_ROUND_NOT_FOUND, APPLICATION_NOT_FOUND
     */
    public ApplicationResponse getMine(Long userId) {
        requireLogin(userId);
        MatchRound round = matchRoundService.getCurrentRound();
        return ApplicationResponse.from(requireActiveApplication(userId, round));
    }

    /**
     * 현재 회차의 내 신청을 수정한다. 인스타 ID 유니크와 공연 규칙을 다시 검사한다(FR-MT-14, FR-MT-05).
     * @throws CustomException UNAUTHORIZED, MATCH_ROUND_NOT_OPEN, APPLICATION_NOT_FOUND, INVALID_INPUT_VALUE,
     *         APPLICATION_INSTAGRAM_DUPLICATE, TIMETABLE_SLOT_NOT_FOUND, APPLICATION_SLOT_NOT_SELECTABLE
     */
    @Transactional
    public ApplicationResponse update(Long userId, UpdateApplicationRequest request) {
        requireLogin(userId);
        MatchRound round = requireAcceptingRound(LocalDateTime.now(clock));
        Application application = requireActiveApplication(userId, round);
        String instagramId = normalizeInstagramId(request.instagramId());
        requireInstagramAvailable(round, instagramId, application);

        application.update(
                instagramId,
                request.nickname(),
                request.gender(),
                request.ageBand(),
                tagsOf(request.tags()),
                request.intro()
        );
        application.changeWantedSlot(wantedSlotService.requireSelectable(round, request.wantedSlotId()));
        return ApplicationResponse.from(saveAndTranslate(application));
    }

    /**
     * 현재 회차의 내 신청을 취소한다(canceled_at 소프트 삭제). 마감 후에는 불가(FR-MT-14).
     * @throws CustomException UNAUTHORIZED, MATCH_ROUND_NOT_OPEN, APPLICATION_NOT_FOUND
     */
    @Transactional
    public void cancel(Long userId) {
        requireLogin(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        MatchRound round = requireAcceptingRound(now);
        requireActiveApplication(userId, round).cancel(now);
    }

    /**
     * 이전 회차에 매칭된 사람이 현재 회차에 다시 참여한다(FR-MT-03). 최근 발표 회차의 내 신청을 REJOIN으로 복사한다.
     * 취소했던 현재 회차 신청이 있으면 새 행 대신 그 행을 되살린다. 보고 싶은 공연은 현재 회차 규칙에 맞을 때만 따라온다(FR-MT-05).
     * <p>검증: 매칭 차단 아님, 현재 회차 접수 중, 최근 발표 회차에서 매칭됨, 현재 회차 유효 신청 없음, 인스타 ID 유니크.
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND, USER_MATCHING_BLOCKED, MATCH_ROUND_NOT_OPEN,
     *         MATCH_RESULT_NOT_PUBLISHED, MATCH_NOT_FOUND, APPLICATION_ALREADY_EXISTS, APPLICATION_INSTAGRAM_DUPLICATE
     */
    @Transactional
    public ApplicationResponse rejoin(Long userId) {
        User user = requireUser(userId);
        requireNotBlocked(user);
        LocalDateTime now = LocalDateTime.now(clock);
        MatchRound current = requireAcceptingRound(now);
        Application source = requireMatchedApplicationInLatestPublishedRound(userId);

        Optional<Application> mine = applicationRepository.findByUser_IdAndRound_Id(userId, current.getId());
        if (mine.filter(application -> !application.isCanceled()).isPresent()) {
            throw new CustomException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        requireInstagramAvailable(current, source.getInstagramId(), mine.orElse(null));

        Application application = mine
                .map(canceled -> restoreFrom(canceled, source, current, now))
                .orElseGet(() -> copyOf(source, user, current, EntryType.REJOIN));
        return ApplicationResponse.from(saveAndTranslate(application));
    }

    /**
     * 공연이 삭제될 때 그 공연을 고른 신청의 선택만 비운다. 신청 자체는 남는다.
     * <p>타임테이블 운영자 서비스가 삭제 직전에 호출한다(H2 테스트 스키마엔 FK SET NULL이 없어 코드로).
     */
    @Transactional
    public void detachWantedSlot(Long slotId) {
        applicationRepository.detachWantedSlot(slotId);
    }

    // 재참여 대상: 최근 발표 회차에서 매칭된 내 신청. 미매칭자는 발표 때 이미 이월됐으므로 대상이 아니다
    private Application requireMatchedApplicationInLatestPublishedRound(Long userId) {
        MatchRound published = matchRoundService.findLatestPublishedRound()
                .orElseThrow(() -> new CustomException(ErrorCode.MATCH_RESULT_NOT_PUBLISHED));
        Application source = requireActiveApplication(userId, published);
        if (!matchRepository.existsByApplication_Id(source.getId())) {
            throw new CustomException(ErrorCode.MATCH_NOT_FOUND);
        }
        return source;
    }

    private static Application restoreFrom(Application canceled, Application source, MatchRound current, LocalDateTime now) {
        canceled.restore();
        canceled.update(
                source.getInstagramId(),
                source.getNickname(),
                source.getGender(),
                source.getAgeBand(),
                source.getTags(),
                source.getIntro()
        );
        canceled.changeWantedSlot(source.wantedSlotFor(current));
        canceled.agree(source.getTermsVersion(), source.getPrivacyVersion(), source.isAgeConfirmed(), now);
        return canceled;
    }

    private static Application copyOf(Application source, User user, MatchRound round, EntryType entryType) {
        return Application.builder()
                .user(user)
                .round(round)
                .instagramId(source.getInstagramId())
                .nickname(source.getNickname())
                .gender(source.getGender())
                .ageBand(source.getAgeBand())
                .tags(source.getTags())
                .intro(source.getIntro())
                .wantedSlot(source.wantedSlotFor(round))
                .entryType(entryType)
                .sourceApplication(source)
                .termsVersion(source.getTermsVersion())
                .privacyVersion(source.getPrivacyVersion())
                .ageConfirmed(source.isAgeConfirmed())
                .agreedAt(source.getAgreedAt())
                .build();
    }

    private User requireUser(Long userId) {
        requireLogin(userId);
        return userService.getUser(userId);
    }

    private static void requireLogin(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
    }

    // 차단 여부는 토큰이 아니라 쓰기 시점의 DB로 본다(FR-AUTH-08)
    private static void requireNotBlocked(User user) {
        if (user.getMatchingBlockedAt() != null) {
            throw new CustomException(ErrorCode.USER_MATCHING_BLOCKED);
        }
    }

    private MatchRound requireAcceptingRound(LocalDateTime now) {
        MatchRound round = matchRoundService.getCurrentRound();
        if (!round.isAcceptingAt(now)) {
            throw new CustomException(ErrorCode.MATCH_ROUND_NOT_OPEN);
        }
        return round;
    }

    private static void requireAgreement(ApplyMatchRequest request) {
        if (!request.ageConfirmed()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private Application requireActiveApplication(Long userId, MatchRound round) {
        return applicationRepository.findByUser_IdAndRound_Id(userId, round.getId())
                .filter(application -> !application.isCanceled())
                .orElseThrow(() -> new CustomException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    // trim → 선행 @ 제거 → 소문자 → 형식 검사(FR-MT-10)
    static String normalizeInstagramId(String raw) {
        String normalized = raw.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!INSTAGRAM_ID.matcher(normalized).matches()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalized;
    }

    // 선검사. 내 행(취소 행 포함)이 이미 그 아이디를 쓰고 있으면 중복이 아니다. 최종 방어는 DB 제약
    private void requireInstagramAvailable(MatchRound round, String instagramId, Application mine) {
        boolean sameAsMine = mine != null && instagramId.equals(mine.getInstagramId());
        if (!sameAsMine && applicationRepository.existsByRound_IdAndInstagramId(round.getId(), instagramId)) {
            throw new CustomException(ErrorCode.APPLICATION_INSTAGRAM_DUPLICATE);
        }
    }

    private static Application newApplication(
            User user,
            MatchRound round,
            ApplyMatchRequest request,
            String instagramId,
            TimetableSlot wantedSlot,
            LocalDateTime now
    ) {
        return Application.builder()
                .user(user)
                .round(round)
                .instagramId(instagramId)
                .nickname(request.nickname())
                .gender(request.gender())
                .ageBand(request.ageBand())
                .tags(tagsOf(request.tags()))
                .intro(request.intro())
                .wantedSlot(wantedSlot)
                .termsVersion(request.termsVersion())
                .privacyVersion(request.privacyVersion())
                .ageConfirmed(request.ageConfirmed())
                .agreedAt(now)
                .build();
    }

    // 취소 후 재신청: uk_app_user_round 때문에 기존 행을 되살리고 내용·동의를 새로 기록한다
    private static Application reapply(
            Application canceled,
            ApplyMatchRequest request,
            String instagramId,
            TimetableSlot wantedSlot,
            LocalDateTime now
    ) {
        canceled.restore();
        canceled.update(
                instagramId,
                request.nickname(),
                request.gender(),
                request.ageBand(),
                tagsOf(request.tags()),
                request.intro()
        );
        canceled.changeWantedSlot(wantedSlot);
        canceled.agree(request.termsVersion(), request.privacyVersion(), request.ageConfirmed(), now);
        return canceled;
    }

    private static Set<MatchTag> tagsOf(Set<MatchTag> tags) {
        return tags == null ? Set.of() : tags;
    }

    // 동시 신청으로 선검사를 통과해도 DB 제약이 잡으면 같은 409로 바꾼다(§5)
    private Application saveAndTranslate(Application application) {
        try {
            return applicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            throw translate(exception);
        }
    }

    private static RuntimeException translate(DataIntegrityViolationException exception) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains(UK_ROUND_INSTA)) {
            return new CustomException(ErrorCode.APPLICATION_INSTAGRAM_DUPLICATE);
        }
        if (message.contains(UK_USER_ROUND)) {
            return new CustomException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        return exception;
    }
}
