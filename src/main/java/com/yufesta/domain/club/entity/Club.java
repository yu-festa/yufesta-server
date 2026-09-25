package com.yufesta.domain.club.entity;

import com.yufesta.common.entity.BaseTimeEntity;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 라인업 동아리 카드(ERD 5, FR-LU-01). 공연 시간·무대는 타임테이블이 club으로 참조해 붙인다.
 * photoUrl은 업로드 API(ClubAdminService.uploadPhoto)만 바꾼다. 카드 정보 수정과 사진 교체는 별개 작업
 */
@Getter
@Entity
@Table(name = "clubs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Club extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 200)
    private String intro;

    @Column(length = 30)
    private String genre;

    @Column(name = "signature_song", length = 50)
    private String signatureSong;

    @Column(name = "instagram_url", length = 200)
    private String instagramUrl;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Builder
    private Club(
            String name,
            String intro,
            String genre,
            String signatureSong,
            String instagramUrl,
            String photoUrl,
            int sortOrder,
            User createdBy
    ) {
        this.name = name;
        this.intro = intro;
        this.genre = genre;
        this.signatureSong = signatureSong;
        this.instagramUrl = instagramUrl;
        this.photoUrl = photoUrl;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
    }

    public void update(
            String name,
            String intro,
            String genre,
            String signatureSong,
            String instagramUrl,
            int sortOrder
    ) {
        this.name = name;
        this.intro = intro;
        this.genre = genre;
        this.signatureSong = signatureSong;
        this.instagramUrl = instagramUrl;
        this.sortOrder = sortOrder;
    }

    /** 대표 사진 URL 교체. null이면 사진 없음 */
    public void changePhoto(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}
