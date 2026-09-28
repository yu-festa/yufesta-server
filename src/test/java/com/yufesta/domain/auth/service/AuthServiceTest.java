package com.yufesta.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.service.UserService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthService authService;

    @Test
    void 로그인_사용자의_역할과_소셜_프로필을_반환한다() {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("google-user")
                .role(UserRole.STAFF)
                .displayName("Google User")
                .profileImageUrl("https://lh3.googleusercontent.com/profile")
                .loginAt(LocalDateTime.of(2026, 10, 8, 12, 0))
                .build();
        when(userService.getUser(1L)).thenReturn(user);

        assertThat(authService.getMe(1L))
                .extracting(response -> response.role(), response -> response.displayName(), response -> response.profileImageUrl())
                .containsExactly(UserRole.STAFF, "Google User", "https://lh3.googleusercontent.com/profile");
    }

    @Test
    void 비로그인이면_UNAUTHORIZED를_던진다() {
        assertThatThrownBy(() -> authService.getMe(null))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
