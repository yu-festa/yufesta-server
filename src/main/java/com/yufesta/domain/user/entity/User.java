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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_provider_user_id",
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

    public User(OAuthProvider provider, String providerUserId, UserRole role) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.role = role;
        recordLogin();
    }

    // 마지막 로그인 시각을 현재 시각으로 갱신
    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
