package com.yufesta.domain.match.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 결과. 방향성 2행(A→B, B→A)으로 저장해 "내 신청" 한 번의 조회로 결과를 읽는다(§8)
 */
@Getter
@Entity
@Table(
        name = "matches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_pair",
                columnNames = {"round_id", "application_id", "partner_application_id"}
        ),
        indexes = @Index(name = "idx_match_app", columnList = "application_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private MatchRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_application_id", nullable = false)
    private Application partnerApplication;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    // 1: 1차 1:1 배정, 2: 2차 라운드 로빈 추가 배정(FR-MT-22)
    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int assignPass;

    @Builder
    private Match(
            MatchRound round,
            Application application,
            Application partnerApplication,
            BigDecimal score,
            int assignPass
    ) {
        this.round = round;
        this.application = application;
        this.partnerApplication = partnerApplication;
        this.score = score;
        this.assignPass = assignPass;
    }
}
