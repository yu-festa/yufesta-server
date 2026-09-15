package com.yufesta.domain.user.repository;

import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
