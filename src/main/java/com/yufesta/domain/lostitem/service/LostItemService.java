package com.yufesta.domain.lostitem.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.lostitem.dto.request.CreateOfficialLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.UpdateLostItemVisibilityRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분실물 게시글 공개 조회와 로그인이 필요한 작성을 처리 */
@Service
@Transactional(readOnly = true)
public class LostItemService {

    private final LostItemRepository lostItemRepository;
    private final UserService userService;
    private final NicknameGenerator nicknameGenerator;

    public LostItemService(
            LostItemRepository lostItemRepository,
            UserService userService,
            NicknameGenerator nicknameGenerator
    ) {
        this.lostItemRepository = lostItemRepository;
        this.userService = userService;
        this.nicknameGenerator = nicknameGenerator;
    }

    /** 숨김 처리되지 않은 분실물 게시글을 최신순으로 조회한다(FR-LF-01). */
    public List<LostItemResponse> getLostItems(int size) {
        return lostItemRepository.findAllByHiddenFalseOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(LostItemResponse::from)
                .toList();
    }

    /**
     * 로그인 사용자의 분실물 게시글을 자동 생성 닉네임과 함께 저장한다(FR-LF-02, FR-AN-01~03).
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND
     */
    @Transactional
    public LostItemResponse create(Long userId, CreateLostItemRequest request) {
        User user = requireUser(userId);
        LostItem lostItem = LostItem.builder()
                .kind(request.kind())
                .description(request.description().trim())
                .placeText(request.placeText().trim())
                .occurredAt(request.occurredAt())
                .displayName(nicknameGenerator.generate())
                .author(user)
                .build();
        return LostItemResponse.from(lostItemRepository.save(lostItem));
    }

    /**
     * 작성자 본인이 자신의 분실물 게시글을 해결 처리한다(FR-LF-05).
     * @throws CustomException UNAUTHORIZED, LOST_ITEM_NOT_FOUND, FORBIDDEN
     */
    @Transactional
    public LostItemResponse resolve(Long userId, Long lostItemId) {
        LostItem lostItem = requireOwnedLostItem(userId, lostItemId);
        lostItem.resolve();
        return LostItemResponse.from(lostItem);
    }

    /**
     * 작성자 본인이 자신의 분실물 게시글을 소프트 삭제한다(FR-LF-05).
     * @throws CustomException UNAUTHORIZED, LOST_ITEM_NOT_FOUND, FORBIDDEN
     */
    @Transactional
    public void delete(Long userId, Long lostItemId) {
        requireOwnedLostItem(userId, lostItemId).hide();
    }

    /**
     * 운영자가 안내소 보관 습득물을 공식 게시글로 등록한다(FR-LF-06).
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND
     */
    @Transactional
    public LostItemResponse createOfficial(Long userId, CreateOfficialLostItemRequest request) {
        User admin = requireUser(userId);
        LostItem lostItem = LostItem.official(
                admin,
                request.description().trim(),
                request.placeText().trim(),
                request.occurredAt()
        );
        return LostItemResponse.from(lostItemRepository.save(lostItem));
    }

    /**
     * 운영자가 분실물 게시글을 해결 처리한다(FR-LF-04, 06).
     * @throws CustomException UNAUTHORIZED, LOST_ITEM_NOT_FOUND
     */
    @Transactional
    public LostItemResponse resolveByAdmin(Long userId, Long lostItemId) {
        requireLogin(userId);
        LostItem lostItem = getLostItemOrThrow(lostItemId);
        lostItem.resolve();
        return LostItemResponse.from(lostItem);
    }

    /**
     * 운영자가 일반 사용자 분실물 게시글을 숨기거나 복구한다(FR-LF-04).
     * <p>공식 안내소 게시글은 ERD 기준 신고·숨김 대상에서 제외한다.
     * @throws CustomException UNAUTHORIZED, LOST_ITEM_NOT_FOUND, FORBIDDEN
     */
    @Transactional
    public LostItemResponse updateVisibility(Long userId, Long lostItemId, UpdateLostItemVisibilityRequest request) {
        requireLogin(userId);
        LostItem lostItem = getLostItemOrThrow(lostItemId);
        if (lostItem.isOfficial()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (request.hidden()) {
            lostItem.hide();
        } else {
            lostItem.restore();
        }
        return LostItemResponse.from(lostItem);
    }

    /**
     * 콘텐츠 신고 전 일반 분실물 게시글을 잠그고 신고 가능 여부를 확인한다.
     * <p>공식 안내소 글과 숨겨진 글은 ERD 기준 신고 대상에서 제외된다.
     * @throws CustomException CONTENT_REPORT_TARGET_NOT_FOUND, CONTENT_NOT_REPORTABLE
     */
    @Transactional
    public void lockReportableForReport(Long lostItemId) {
        LostItem lostItem = getLostItemForUpdate(lostItemId);
        if (lostItem.isOfficial() || lostItem.isHidden()) {
            throw new CustomException(ErrorCode.CONTENT_NOT_REPORTABLE);
        }
    }

    /**
     * 신고 수를 원자적으로 늘리고 임계값에 도달하면 숨긴다.
     * <p>호출 전 lockReportableForReport로 같은 행을 잠가야 한다.
     */
    @Transactional
    public void incrementReportCountAndHideIfThreshold(Long lostItemId, int hideThreshold) {
        lostItemRepository.incrementReportCount(lostItemId);
        LostItem lostItem = getLostItemForUpdate(lostItemId);
        if (lostItem.getReportCount() >= hideThreshold) {
            lostItem.hide();
        }
    }

    private LostItem requireOwnedLostItem(Long userId, Long lostItemId) {
        requireLogin(userId);
        LostItem lostItem = getLostItemOrThrow(lostItemId);
        if (!lostItem.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return lostItem;
    }

    private LostItem getLostItemOrThrow(Long lostItemId) {
        return lostItemRepository.findById(lostItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOST_ITEM_NOT_FOUND));
    }

    private LostItem getLostItemForUpdate(Long lostItemId) {
        return lostItemRepository.findByIdForUpdate(lostItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
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
}
