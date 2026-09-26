package com.yufesta.domain.lostitem.comment.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 분실물 게시글의 최상위 댓글 또는 답글. 답글은 최상위 댓글에만 연결한다. */
@Getter
@Entity
@Table(name = "lost_item_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LostItemComment extends BaseTimeEntity {

    public static final String DELETED_CONTENT = "삭제된 댓글입니다.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_item_id", nullable = false)
    private LostItem lostItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private LostItemComment parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(nullable = false, length = 20)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModerationStatus moderationStatus;

    @Column(nullable = false)
    private int reportCount;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Builder
    private LostItemComment(
            LostItem lostItem,
            LostItemComment parent,
            User author,
            String content,
            String displayName
    ) {
        this.lostItem = lostItem;
        this.parent = parent;
        this.author = author;
        this.content = content;
        this.displayName = displayName;
        this.moderationStatus = ModerationStatus.PASSED;
        this.reportCount = 0;
        this.hidden = false;
        this.deleted = false;
    }

    /** 답글은 최상위 댓글에만 달 수 있다. */
    public boolean isTopLevel() {
        return parent == null;
    }

    /** 삭제된 댓글은 본문을 숨기고 안내 문구만 공개한다. */
    public String getDisplayContent() {
        return deleted ? DELETED_CONTENT : content;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && author != null && author.getId().equals(userId);
    }

    public void delete() {
        this.deleted = true;
    }

    public void hide() {
        this.hidden = true;
    }

    public void restore() {
        this.hidden = false;
    }
}
