package com.yufesta.domain.lostitem.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 분실물 게시글에 연결되는 본문용·썸네일 이미지 한 쌍 */
@Getter
@Entity
@Table(
        name = "lost_item_images",
        uniqueConstraints = @UniqueConstraint(name = "uk_lost_item_images_lost_item", columnNames = "lost_item_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LostItemImage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_item_id", nullable = false)
    private LostItem lostItem;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 500)
    private String thumbnailUrl;

    @Builder
    private LostItemImage(LostItem lostItem, String imageUrl, String thumbnailUrl) {
        this.lostItem = lostItem;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
    }
}
