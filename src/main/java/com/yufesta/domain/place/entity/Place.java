package com.yufesta.domain.place.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.place.enums.PlaceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지도에 표시할 축제 장소
 */
@Getter
@Entity
@Table(name = "places")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlaceCategory category;

    @Column(name = "lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 200)
    private String description;

    @Column(length = 50)
    private String building;

    @Column(length = 20)
    private String floor;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private Place(
            String name,
            PlaceCategory category,
            BigDecimal latitude,
            BigDecimal longitude,
            String description,
            String building,
            String floor,
            int sortOrder,
            boolean active
    ) {
        this.name = name;
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.building = building;
        this.floor = floor;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
