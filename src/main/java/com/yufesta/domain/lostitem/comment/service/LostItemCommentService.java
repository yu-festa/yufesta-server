package com.yufesta.domain.lostitem.comment.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.lostitem.comment.dto.request.CreateLostItemCommentRequest;
import com.yufesta.domain.lostitem.comment.dto.request.UpdateLostItemCommentVisibilityRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import com.yufesta.domain.lostitem.comment.entity.LostItemComment;
import com.yufesta.domain.lostitem.comment.entity.LostItemCommentAlias;
import com.yufesta.domain.lostitem.comment.repository.LostItemCommentAliasRepository;
import com.yufesta.domain.lostitem.comment.repository.LostItemCommentRepository;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.report.dto.response.ContentTargetStatus;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분실물 글의 공개 댓글·답글, 익명 별칭, 삭제·운영자 숨김을 처리한다. */
@Service
@Transactional(readOnly = true)
public class LostItemCommentService {

    private static final int MAX_ALIAS_GENERATION_ATTEMPTS = 20;

    private final LostItemRepository lostItemRepository;
    private final LostItemCommentRepository commentRepository;
    private final LostItemCommentAliasRepository aliasRepository;
    private final UserService userService;
    private final NicknameGenerator nicknameGenerator;

    public LostItemCommentService(
            LostItemRepository lostItemRepository,
            LostItemCommentRepository commentRepository,
            LostItemCommentAliasRepository aliasRepository,
            UserService userService,
            NicknameGenerator nicknameGenerator
    ) {
        this.lostItemRepository = lostItemRepository;
        this.commentRepository = commentRepository;
        this.aliasRepository = aliasRepository;
        this.userService = userService;
        this.nicknameGenerator = nicknameGenerator;
    }

    /** 노출 중인 분실물 글의 최상위 댓글과 답글을 작성 시각순으로 반환한다. */
    public List<LostItemCommentResponse> getComments(Long userId, Long lostItemId) {
        LostItem lostItem = getVisibleLostItem(lostItemId);
        List<LostItemComment> comments = commentRepository.findAllByLostItem_IdAndHiddenFalseOrderByCreatedAtAsc(lostItemId);
        Long lostItemAuthorId = lostItem.getAuthor() == null ? null : lostItem.getAuthor().getId();
        Map<Long, List<LostItemComment>> repliesByParentId = comments.stream()
                .filter(comment -> !comment.isTopLevel())
                .collect(Collectors.groupingBy(comment -> comment.getParent().getId()));

        return comments.stream()
                .filter(LostItemComment::isTopLevel)
                .map(comment -> toResponse(comment, userId, lostItemAuthorId, repliesByParentId.get(comment.getId())))
                .toList();
    }

    /** 로그인 사용자가 최상위 댓글을 작성한다. */
    @Transactional
    public LostItemCommentResponse create(Long userId, Long lostItemId, CreateLostItemCommentRequest request) {
        LostItem lostItem = getVisibleLostItem(lostItemId);
        User author = requireUser(userId);
        LostItemComment comment = saveComment(lostItem, null, author, request);
        return toResponse(comment, userId, lostItem.getAuthor() == null ? null : lostItem.getAuthor().getId(), List.of());
    }

    /** 로그인 사용자가 최상위 댓글에 답글을 작성한다. */
    @Transactional
    public LostItemCommentResponse reply(
            Long userId,
            Long lostItemId,
            Long parentCommentId,
            CreateLostItemCommentRequest request
    ) {
        LostItem lostItem = getVisibleLostItem(lostItemId);
        LostItemComment parent = getCommentInLostItem(parentCommentId, lostItemId);
        if (!parent.isTopLevel() || parent.isDeleted() || parent.isHidden()) {
            throw new CustomException(ErrorCode.LOST_ITEM_COMMENT_REPLY_NOT_ALLOWED);
        }
        User author = requireUser(userId);
        LostItemComment reply = saveComment(lostItem, parent, author, request);
        return toResponse(reply, userId, lostItem.getAuthor() == null ? null : lostItem.getAuthor().getId(), List.of());
    }

    /** 작성자는 자신의 댓글 또는 답글을 소프트 삭제한다. */
    @Transactional
    public void delete(Long userId, Long lostItemId, Long commentId) {
        requireLogin(userId);
        getVisibleLostItem(lostItemId);
        LostItemComment comment = getCommentInLostItem(commentId, lostItemId);
        if (!comment.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        comment.delete();
    }

    /** 운영자가 댓글을 숨기거나 다시 공개한다. */
    @Transactional
    public LostItemCommentResponse updateVisibility(
            Long lostItemId,
            Long commentId,
            UpdateLostItemCommentVisibilityRequest request
    ) {
        getVisibleLostItem(lostItemId);
        LostItemComment comment = getCommentInLostItem(commentId, lostItemId);
        if (request.hidden()) {
            comment.hide();
        } else {
            comment.restore();
        }
        Long lostItemAuthorId = comment.getLostItem().getAuthor() == null
                ? null
                : comment.getLostItem().getAuthor().getId();
        return toResponse(comment, null, lostItemAuthorId, List.of());
    }

    /** 콘텐츠 신고 전 댓글 행을 잠그고 신고 가능한 공개 댓글인지 확인한다. */
    @Transactional
    public void lockReportableForReport(Long commentId) {
        LostItemComment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
        if (comment.isHidden() || comment.isDeleted() || comment.getLostItem().isHidden()) {
            throw new CustomException(ErrorCode.CONTENT_NOT_REPORTABLE);
        }
    }

    /** 신고 수를 원자적으로 늘리고 임계값에 도달하면 해당 댓글을 숨긴다. */
    @Transactional
    public void incrementReportCountAndHideIfThreshold(Long commentId, int hideThreshold) {
        commentRepository.incrementReportCount(commentId);
        LostItemComment comment = commentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
        if (comment.getReportCount() >= hideThreshold) {
            comment.hide();
        }
    }

    /** 운영자 신고 목록에 댓글의 현재 신고·숨김 상태를 제공한다. */
    public ContentTargetStatus getTargetStatusForAdmin(Long commentId) {
        LostItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOST_ITEM_COMMENT_NOT_FOUND));
        return new ContentTargetStatus(comment.getReportCount(), comment.isHidden());
    }

    private LostItemComment saveComment(
            LostItem lostItem,
            LostItemComment parent,
            User author,
            CreateLostItemCommentRequest request
    ) {
        LostItemComment comment = LostItemComment.builder()
                .lostItem(lostItem)
                .parent(parent)
                .author(author)
                .content(request.content().trim())
                .displayName(getOrCreateAlias(lostItem, author))
                .build();
        return commentRepository.save(comment);
    }

    private String getOrCreateAlias(LostItem lostItem, User user) {
        Optional<LostItemCommentAlias> existing = aliasRepository.findByLostItem_IdAndUser_Id(lostItem.getId(), user.getId());
        if (existing.isPresent()) {
            return existing.get().getDisplayName();
        }

        for (int attempt = 0; attempt < MAX_ALIAS_GENERATION_ATTEMPTS; attempt++) {
            String displayName = nicknameGenerator.generate();
            if (aliasRepository.existsByLostItem_IdAndDisplayName(lostItem.getId(), displayName)) {
                continue;
            }
            try {
                return aliasRepository.save(LostItemCommentAlias.builder()
                        .lostItem(lostItem)
                        .user(user)
                        .displayName(displayName)
                        .build()).getDisplayName();
            } catch (DataIntegrityViolationException exception) {
                Optional<LostItemCommentAlias> concurrentlyCreated = aliasRepository
                        .findByLostItem_IdAndUser_Id(lostItem.getId(), user.getId());
                if (concurrentlyCreated.isPresent()) {
                    return concurrentlyCreated.get().getDisplayName();
                }
            }
        }
        throw new CustomException(ErrorCode.APP_SETTING_INVALID);
    }

    private LostItemCommentResponse toResponse(
            LostItemComment comment,
            Long userId,
            Long lostItemAuthorId,
            List<LostItemComment> replies
    ) {
        List<LostItemCommentResponse> replyResponses = replies == null
                ? List.of()
                : replies.stream()
                        .map(reply -> toResponse(reply, userId, lostItemAuthorId, List.of()))
                        .toList();
        return LostItemCommentResponse.from(comment, userId, lostItemAuthorId, replyResponses);
    }

    private LostItem getVisibleLostItem(Long lostItemId) {
        LostItem lostItem = lostItemRepository.findById(lostItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOST_ITEM_NOT_FOUND));
        if (lostItem.isHidden()) {
            throw new CustomException(ErrorCode.LOST_ITEM_NOT_FOUND);
        }
        return lostItem;
    }

    private LostItemComment getCommentInLostItem(Long commentId, Long lostItemId) {
        LostItemComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOST_ITEM_COMMENT_NOT_FOUND));
        if (!comment.getLostItem().getId().equals(lostItemId)) {
            throw new CustomException(ErrorCode.LOST_ITEM_COMMENT_NOT_FOUND);
        }
        return comment;
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
