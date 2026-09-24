package com.yufesta.domain.lostitem.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.enums.LostItemStatus;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 사용자가 익명 표시명으로 작성하는 분실물 게시글 */
@Getter
@Entity
@Table(name = "lost_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LostItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private LostItemKind kind;

    @Column(nullable = false, length = 100)
    private String description;

    @Column(nullable = false, length = 50)
    private String placeText;

    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LostItemStatus status;

    @Column(nullable = false, length = 20)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private User author;

    @Column(name = "is_official", nullable = false)
    private boolean official;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModerationStatus moderationStatus;

    @Column(nullable = false)
    private int reportCount;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Builder
    private LostItem(
            LostItemKind kind,
            String description,
            String placeText,
            LocalDateTime occurredAt,
            String displayName,
            User author
    ) {
        this.kind = kind;
        this.description = description;
        this.placeText = placeText;
        this.occurredAt = occurredAt;
        this.status = LostItemStatus.OPEN;
        this.displayName = displayName;
        this.author = author;
        this.official = false;
        this.moderationStatus = ModerationStatus.PASSED;
        this.reportCount = 0;
        this.hidden = false;
    }
}
