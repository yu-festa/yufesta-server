package com.yufesta.domain.user.repository;

import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    // 인증 필터용. 역할만 필요하므로 엔티티를 통째로 읽지 않는다
    @Query("select u.role from User u where u.id = :id")
    Optional<UserRole> findRoleById(@Param("id") Long id);

    // 신고 누적 제재 판정 전 대상 행을 잠근다(§5). 같은 대상에 대한 동시 신고가 임계 검사를 건너뛰지 못하게 한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
