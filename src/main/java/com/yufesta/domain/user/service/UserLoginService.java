package com.yufesta.domain.user.service;

import com.yufesta.domain.appsetting.service.AdminAllowlistService;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 계정으로 사용자를 조회하거나 최초 로그인 사용자를 생성
 */
@Service
@Transactional(readOnly = true)
public class UserLoginService {

    private final UserRepository userRepository;
    private final AdminAllowlistService adminAllowlistService;
    private final Clock clock;

    public UserLoginService(
            UserRepository userRepository,
            AdminAllowlistService adminAllowlistService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.adminAllowlistService = adminAllowlistService;
        this.clock = clock;
    }

    /**
     * 소셜 계정으로 로그인한다. 기존 사용자는 로그인 시각을 갱신하고, 신규 사용자는 생성한다.
     * <p>역할은 최초 로그인 시 admin.allowlist 포함 여부로만 정해진다.
     */
    @Transactional
    public User login(OAuthProvider provider, String providerUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(user -> {
                    user.recordLogin(now);
                    return user;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .role(resolveRole(provider, providerUserId))
                        .loginAt(now)
                        .build()));
    }

    // 허용 목록에 있는 신규 사용자에게 STAFF 역할 부여
    private UserRole resolveRole(OAuthProvider provider, String providerUserId) {
        return adminAllowlistService.contains(provider, providerUserId)
                ? UserRole.STAFF
                : UserRole.USER;
    }
}
