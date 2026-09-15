package com.yufesta.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yufesta.domain.appsetting.service.AdminAllowlistService;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAllowlistService adminAllowlistService;

    @InjectMocks
    private UserLoginService userLoginService;

    @Test
    void 허용_목록의_신규_사용자는_staff로_생성한다() {
        when(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(adminAllowlistService.contains(OAuthProvider.KAKAO, "12345")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userLoginService.login(OAuthProvider.KAKAO, "12345");

        assertThat(user.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void 허용_목록_밖의_신규_사용자는_user로_생성한다() {
        when(userRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-user"))
                .thenReturn(Optional.empty());
        when(adminAllowlistService.contains(OAuthProvider.GOOGLE, "google-user")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userLoginService.login(OAuthProvider.GOOGLE, "google-user");

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }
}
