package com.yufesta.domain.user.entity;

import com.yufesta.common.entity.BaseTimeEntity;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
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
 * 소셜 로그인 회원. 운영자도 role로만 구분한다
 */
@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider",
                columnNames = {"provider", "provider_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, updatable = false, length = 191)
    private String providerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Column(length = 30)
    private String displayName;

    private LocalDateTime matchingBlockedAt;

    private LocalDateTime writeBannedAt;

    private LocalDateTime lastLoginAt;

    @Builder
    private User(OAuthProvider provider, String providerUserId, UserRole role, LocalDateTime loginAt) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.role = role;
        this.lastLoginAt = loginAt;
    }

    // 시각은 호출 측이 Clock으로 구해 넘긴다. 엔티티는 시계를 모른다
    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    public boolean isMatchingBlocked() {
        return matchingBlockedAt != null;
    }

    // 신고 누적 제재(FR-MT-41). 이후 신청·재참여가 USER_MATCHING_BLOCKED로 거부된다
    public void blockMatching(LocalDateTime now) {
        this.matchingBlockedAt = now;
    }

    // 운영자 검토로 제재가 취소될 때(FR-MT-42). 이미 삭제된 신청은 되살리지 않는다
    public void unblockMatching() {
        this.matchingBlockedAt = null;
    }
}
