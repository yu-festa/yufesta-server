package com.yufesta.domain.place.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 장소에서 진행하는 이벤트
 */
@Getter
@Entity
@Table(name = "place_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "time_text", nullable = false, length = 30)
    private String timeText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private PlaceEvent(Place place, String name, String timeText, int sortOrder) {
        this.place = place;
        this.name = name;
        this.timeText = timeText;
        this.sortOrder = sortOrder;
    }

    public void update(String name, String timeText, int sortOrder) {
        this.name = name;
        this.timeText = timeText;
        this.sortOrder = sortOrder;
    }
}
