package com.yufesta.domain.timetable.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.timetable.enums.SlotType;
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

/**
 * 타임테이블 한 항목(ERD 6). 순서 변경·지연·LIVE 수동 지정을 전부 이 행의 컬럼으로 표현한다(FR-TT-02~04).
 * <ul>
 *   <li>{@code changedFromStart}: 시작 시각이 처음 바뀔 때의 원래 시각. 프론트가 취소선으로 보여준다</li>
 *   <li>{@code delayMinutes}: 운영자가 넣는 지연 분. 실제 시각 계산({@link #getEffectiveStartAt()})에만 쓰고 원래 시각은 남긴다</li>
 *   <li>{@code liveOverride}: true면 시계 판정보다 우선해 이 항목만 LIVE</li>
 * </ul>
 */
@Getter
@Entity
@Table(name = "timetable_slots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 50)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 10)
    private SlotType slotType;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_place_id", nullable = false)
    private Place stage;

    // 출연 동아리. EVENT·GUEST는 null. 동아리가 삭제되면 detachClub()으로 비운다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(name = "changed_from_start")
    private LocalDateTime changedFromStart;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Column(name = "is_live_override")
    private Boolean liveOverride;

    @Builder
    private TimetableSlot(
            int sortOrder,
            String title,
            SlotType slotType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Place stage,
            Club club
    ) {
        this.sortOrder = sortOrder;
        this.title = title;
        this.slotType = slotType;
        this.startAt = startAt;
        this.endAt = endAt;
        this.stage = stage;
        this.club = club;
    }

    public void update(String title, SlotType slotType, Place stage, Club club) {
        this.title = title;
        this.slotType = slotType;
        this.stage = stage;
        this.club = club;
    }

    /** 동아리가 삭제될 때 공연은 남기고 연결만 끊는다(ERD: club_id ON DELETE SET NULL과 같은 효과) */
    public void detachClub() {
        this.club = null;
    }

    /** 시작·종료 시각을 바꾼다. 취소선은 "원래 시각" 기준이므로 시작 시각이 처음 바뀔 때만 기록하고 이후엔 덮어쓰지 않는다(FR-TT-04) */
    public void changeTimes(LocalDateTime startAt, LocalDateTime endAt) {
        if (changedFromStart == null && !this.startAt.equals(startAt)) {
            changedFromStart = this.startAt;
        }
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /** 지연 분을 넣거나(null이면) 해제한다. 원래 시각은 바뀌지 않는다(FR-TT-03) */
    public void delay(Integer minutes) {
        this.delayMinutes = minutes;
    }

    public void markLive() {
        this.liveOverride = true;
    }

    public void clearLive() {
        this.liveOverride = null;
    }

    public void reorder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isLiveOverride() {
        return Boolean.TRUE.equals(liveOverride);
    }

    public boolean isChanged() {
        return changedFromStart != null;
    }

    /** 지연을 반영한 실제 시작 시각. LIVE 판정과 홈 "다음 공연" 계산 기준 */
    public LocalDateTime getEffectiveStartAt() {
        return startAt.plusMinutes(delayOrZero());
    }

    public LocalDateTime getEffectiveEndAt() {
        return endAt.plusMinutes(delayOrZero());
    }

    /** 시계 기준 LIVE 여부: 실제 시작 ≤ now < 실제 종료. 수동 지정 우선 규칙은 서비스가 적용한다 */
    public boolean isLiveAt(LocalDateTime now) {
        return !now.isBefore(getEffectiveStartAt()) && now.isBefore(getEffectiveEndAt());
    }

    private int delayOrZero() {
        return delayMinutes == null ? 0 : delayMinutes;
    }
}
