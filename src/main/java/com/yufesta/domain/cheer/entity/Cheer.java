package com.yufesta.domain.cheer.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비로그인 익명 키로 작성하는 응원 메시지 */
@Getter
@Entity
@Table(name = "cheers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cheer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String content;

    @Column(nullable = false, length = 20)
    private String displayName;

    @Column(length = 64)
    private String writerKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ModerationStatus moderationStatus;

    @Column(nullable = false)
    private int reportCount;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Builder
    private Cheer(String content, String displayName, String writerKeyHash) {
        this.content = content;
        this.displayName = displayName;
        this.writerKeyHash = writerKeyHash;
        this.moderationStatus = ModerationStatus.PASSED;
        this.reportCount = 0;
        this.hidden = false;
    }

    /** 신고 누적 또는 운영자 조치로 공개 목록에서 숨긴다. */
    public void hide() {
        this.hidden = true;
    }

    /** 운영자 판단에 따라 숨긴 메시지를 다시 공개한다. */
    public void restore() {
        this.hidden = false;
    }
}
