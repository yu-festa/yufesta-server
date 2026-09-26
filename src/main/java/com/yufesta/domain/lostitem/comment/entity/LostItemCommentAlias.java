package com.yufesta.domain.lostitem.comment.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 같은 분실물 글 안에서만 유지하는 사용자별 익명 닉네임. */
@Getter
@Entity
@Table(
        name = "lost_item_comment_aliases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lost_item_comment_alias_user",
                        columnNames = {"lost_item_id", "user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_lost_item_comment_alias_name",
                        columnNames = {"lost_item_id", "display_name"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LostItemCommentAlias extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_item_id", nullable = false)
    private LostItem lostItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "display_name", nullable = false, length = 20)
    private String displayName;

    @Builder
    private LostItemCommentAlias(LostItem lostItem, User user, String displayName) {
        this.lostItem = lostItem;
        this.user = user;
        this.displayName = displayName;
    }
}
