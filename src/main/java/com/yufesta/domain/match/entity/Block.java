package com.yufesta.domain.match.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.match.enums.BlockDecision;
import com.yufesta.domain.match.enums.BlockReason;
import com.yufesta.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 상대 신고·차단. 신고자–대상 쌍은 1건이며 이후 회차에서 두 사람은 다시 매칭되지 않는다(FR-MT-41)
 */
@Getter
@Entity
@Table(
        name = "blocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_blocks_pair",
                columnNames = {"reporter_user_id", "target_user_id"}
        ),
        indexes = @Index(name = "idx_blocks_target", columnList = "target_user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User target;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id")
    private MatchRound round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlockReason reason;

    @Column(length = 500)
    private String detail;

    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private BlockDecision decision;

    @Builder
    private Block(User reporter, User target, MatchRound round, BlockReason reason, String detail) {
        this.reporter = reporter;
        this.target = target;
        this.round = round;
        this.reason = reason;
        this.detail = detail;
    }

    // 운영자 검토(FR-MT-42). DISMISS된 신고는 누적 계산에서 빠진다
    public void review(BlockDecision decision, LocalDateTime now) {
        this.decision = decision;
        this.reviewedAt = now;
    }
}
