package com.yufesta.common.security.oauth2;

import com.yufesta.domain.user.enums.OAuthProvider;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 카카오와 구글의 서로 다른 사용자 식별자 속성을 공통 문자열로 변환
 */
@Component
public class OAuth2ProviderUserIdExtractor {

    // 제공자별 식별자 속성을 공통 사용자 ID로 변환
    public String extract(OAuthProvider provider, OAuth2User oauth2User) {
        String attributeName = provider == OAuthProvider.KAKAO ? "id" : "sub";
        Object attribute = oauth2User.getAttributes().get(attributeName);

        if (attribute == null) {
            throw new IllegalArgumentException("OAuth provider user id is missing");
        }
        return String.valueOf(attribute);
    }
}
