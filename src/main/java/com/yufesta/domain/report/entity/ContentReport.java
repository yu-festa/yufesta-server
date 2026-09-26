package com.yufesta.domain.report.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.report.enums.ContentTargetType;
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

/** 응원 메시지와 분실물 게시글의 공통 신고 기록 */
@Getter
@Entity
@Table(
        name = "content_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_report_once",
                columnNames = {"target_type", "target_id", "reporter_user_id"}
        ),
        indexes = @Index(name = "idx_report_target", columnList = "target_type,target_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private User reporter;

    @Column(nullable = false, length = 20)
    private String reason;

    private LocalDateTime reviewedAt;

    @Builder
    private ContentReport(ContentTargetType targetType, Long targetId, User reporter, String reason) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporter = reporter;
        this.reason = reason;
    }

    /** 운영자가 신고를 확인한 시각을 기록한다. */
    public void review(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
