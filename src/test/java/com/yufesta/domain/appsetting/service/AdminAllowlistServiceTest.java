package com.yufesta.domain.appsetting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.user.enums.OAuthProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAllowlistServiceTest {

    @Mock
    private AppSettingReader appSettingReader;

    @InjectMocks
    private AdminAllowlistService adminAllowlistService;

    @Test
    void provider와_계정_식별자가_모두_일치할_때만_허용한다() {
        when(appSettingReader.getList(SettingKey.ADMIN_ALLOWLIST))
                .thenReturn(List.of("KAKAO:12345", "GOOGLE:google-user", "KAKAO", ":no-provider"));

        assertThat(adminAllowlistService.contains(OAuthProvider.KAKAO, "12345")).isTrue();
        assertThat(adminAllowlistService.contains(OAuthProvider.GOOGLE, "google-user")).isTrue();
        assertThat(adminAllowlistService.contains(OAuthProvider.KAKAO, "google-user")).isFalse();
        assertThat(adminAllowlistService.contains(OAuthProvider.KAKAO, "no-provider")).isFalse();
    }

    @Test
    void 허용_목록이_비어_있으면_아무도_허용하지_않는다() {
        when(appSettingReader.getList(SettingKey.ADMIN_ALLOWLIST)).thenReturn(List.of());

        assertThat(adminAllowlistService.contains(OAuthProvider.KAKAO, "12345")).isFalse();
    }
}
