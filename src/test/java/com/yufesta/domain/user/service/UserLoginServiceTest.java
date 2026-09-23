package com.yufesta.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yufesta.domain.appsetting.service.AdminAllowlistService;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 15, 30);

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAllowlistService adminAllowlistService;

    private UserLoginService userLoginService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        userLoginService = new UserLoginService(userRepository, adminAllowlistService, fixedClock);
    }

    @Test
    void 허용_목록의_신규_사용자는_staff로_생성하고_로그인_시각은_Clock_기준이다() {
        when(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.empty());
        when(adminAllowlistService.contains(OAuthProvider.KAKAO, "12345")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userLoginService.login(OAuthProvider.KAKAO, "12345");

        assertThat(user.getRole()).isEqualTo(UserRole.STAFF);
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
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

    @Test
    void 기존_사용자는_역할을_유지하고_로그인_시각만_갱신한다() {
        User existing = User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId("12345")
                .role(UserRole.USER)
                .loginAt(NOW.minusDays(3))
                .build();
        when(userRepository.findByProviderAndProviderUserId(OAuthProvider.KAKAO, "12345"))
                .thenReturn(Optional.of(existing));

        User user = userLoginService.login(OAuthProvider.KAKAO, "12345");

        assertThat(user).isSameAs(existing);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getLastLoginAt()).isEqualTo(NOW);
    }
}
