package com.yufesta.domain.appsetting.service;

import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.user.enums.OAuthProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 허용 목록에 포함된 소셜 계정을 확인
 * 설정값은 쉼표로 구분한 {@code PROVIDER:providerUserId} 형식
 */
@Service
@Transactional(readOnly = true)
public class AdminAllowlistService {

    private final AppSettingReader appSettingReader;

    public AdminAllowlistService(AppSettingReader appSettingReader) {
        this.appSettingReader = appSettingReader;
    }

    /** 운영자 허용 목록에 현재 소셜 계정이 있는지 확인한다. */
    public boolean contains(OAuthProvider provider, String providerUserId) {
        return appSettingReader.getList(SettingKey.ADMIN_ALLOWLIST).stream()
                .anyMatch(account -> matches(provider, providerUserId, account));
    }

    // 설정값 한 항목이 제공자와 사용자 ID 모두에 일치하는지 확인
    private boolean matches(OAuthProvider provider, String providerUserId, String account) {
        String[] parts = account.split(":", 2);
        return parts.length == 2
                && provider.name().equalsIgnoreCase(parts[0])
                && providerUserId.equals(parts[1]);
    }
}
