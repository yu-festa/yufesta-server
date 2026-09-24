package com.yufesta.domain.match.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.match.enums.RoundStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 회차. 시각은 운영자가 수정하는 값이며 상태 전이는 한 방향으로만 허용한다(FR-MT-01·02)
 */
@Getter
@Entity
@Table(
        name = "match_rounds",
        uniqueConstraints = @UniqueConstraint(name = "uk_rounds_seq", columnNames = "seq")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRound extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private int seq;

    @Column(nullable = false)
    private LocalDateTime openAt;

    @Column(nullable = false)
    private LocalDateTime closeAt;

    @Column(nullable = false)
    private LocalDateTime publishAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RoundStatus status;

    private LocalDateTime executedAt;

    private LocalDateTime publishedAt;

    @Builder
    private MatchRound(int seq, LocalDateTime openAt, LocalDateTime closeAt, LocalDateTime publishAt) {
        this.seq = seq;
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.publishAt = publishAt;
        this.status = RoundStatus.SCHEDULED;
    }

    // 신청·수정·취소가 가능한 시점: 접수 중이고 마감 전(FR-MT-02)
    public boolean isAcceptingAt(LocalDateTime now) {
        return status == RoundStatus.OPEN && now.isBefore(closeAt);
    }

    // 결과 노출 기준은 status가 아니라 published_at(FR-MT-04)
    /**
     * 이 회차 신청에서 고를 수 있는 공연인지: 발표 시각 이후에 시작하는 공연만(FR-MT-05).
     * 신청·수정·이월 복사·배치 재검증이 전부 이 규칙 하나를 쓴다. 지연 전 원래 시작 시각 기준
     */
    public boolean allowsWantedSlot(LocalDateTime slotStartAt) {
        return !slotStartAt.isBefore(publishAt);
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void open() {
        transition(RoundStatus.SCHEDULED, RoundStatus.OPEN);
    }

    public void close() {
        transition(RoundStatus.OPEN, RoundStatus.CLOSED);
    }

    // 배치가 끝난 시각. 있으면 스케줄러가 같은 회차를 다시 돌리지 않는다(멱등)
    public void markExecuted(LocalDateTime now) {
        this.executedAt = now;
    }

    public void publish(LocalDateTime now) {
        transition(RoundStatus.CLOSED, RoundStatus.PUBLISHED);
        this.publishedAt = now;
    }

    public void updateTimes(LocalDateTime openAt, LocalDateTime closeAt, LocalDateTime publishAt) {
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.publishAt = publishAt;
    }

    private void transition(RoundStatus from, RoundStatus to) {
        if (status != from) {
            throw new CustomException(ErrorCode.MATCH_ROUND_INVALID_STATUS);
        }
        this.status = to;
    }
}
