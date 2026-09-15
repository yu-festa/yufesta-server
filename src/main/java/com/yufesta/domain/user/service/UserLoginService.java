package com.yufesta.domain.user.service;

import com.yufesta.domain.appsetting.service.AdminAllowlistService;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 계정으로 사용자를 조회하거나 최초 로그인 사용자를 생성
 */
@Service
public class UserLoginService {

    private final UserRepository userRepository;
    private final AdminAllowlistService adminAllowlistService;

    public UserLoginService(UserRepository userRepository, AdminAllowlistService adminAllowlistService) {
        this.userRepository = userRepository;
        this.adminAllowlistService = adminAllowlistService;
    }

    @Transactional
    // 기존 사용자는 로그인 시각을 갱신하고, 신규 사용자는 생성
    public User login(OAuthProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(user -> {
                    user.recordLogin();
                    return user;
                })
                .orElseGet(() -> userRepository.save(new User(
                        provider,
                        providerUserId,
                        resolveRole(provider, providerUserId)
                )));
    }

    // 허용 목록에 있는 신규 사용자에게 STAFF 역할 부여
    private UserRole resolveRole(OAuthProvider provider, String providerUserId) {
        return adminAllowlistService.contains(provider, providerUserId)
                ? UserRole.STAFF
                : UserRole.USER;
    }
}
