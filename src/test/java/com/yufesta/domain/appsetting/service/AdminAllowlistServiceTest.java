package com.yufesta.domain.appsetting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yufesta.domain.appsetting.entity.AppSetting;
import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import com.yufesta.domain.user.enums.OAuthProvider;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAllowlistServiceTest {

    @Mock
    private AppSettingRepository appSettingRepository;

    @InjectMocks
    private AdminAllowlistService adminAllowlistService;

    @Test
    void provider와_계정_식별자가_모두_일치할_때만_허용한다() {
        AppSetting allowlist = new AppSetting(
                "admin.allowlist",
                "KAKAO:12345, GOOGLE:google-user",
                "운영자 허용 목록"
        );
        when(appSettingRepository.findById("admin.allowlist")).thenReturn(Optional.of(allowlist));

        assertThat(adminAllowlistService.contains(OAuthProvider.KAKAO, "12345")).isTrue();
        assertThat(adminAllowlistService.contains(OAuthProvider.GOOGLE, "google-user")).isTrue();
        assertThat(adminAllowlistService.contains(OAuthProvider.KAKAO, "google-user")).isFalse();
    }
}
