package com.yufesta.common.security.oauth2;

import com.yufesta.domain.user.enums.OAuthProvider;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 카카오·구글의 서로 다른 사용자 속성에서 식별자와 공개 프로필을 추출한다. */
@Component
public class OAuth2UserProfileExtractor {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 2048;

    public OAuth2UserProfile extract(OAuthProvider provider, OAuth2User oauth2User) {
        return switch (provider) {
            case KAKAO -> extractKakao(oauth2User.getAttributes());
            case GOOGLE -> extractGoogle(oauth2User.getAttributes());
        };
    }

    private OAuth2UserProfile extractKakao(Map<String, Object> attributes) {
        String providerUserId = requiredText(attributes.get("id"));
        Map<String, Object> account = nestedMap(attributes.get("kakao_account"));
        Map<String, Object> profile = nestedMap(account.get("profile"));
        String displayName = displayName(profile.get("nickname"));
        String profileImageUrl = profileImageUrl(profile.get("profile_image_url"));
        if (profileImageUrl == null) {
            profileImageUrl = profileImageUrl(profile.get("thumbnail_image_url"));
        }
        return new OAuth2UserProfile(providerUserId, displayName, profileImageUrl);
    }

    private OAuth2UserProfile extractGoogle(Map<String, Object> attributes) {
        return new OAuth2UserProfile(
                requiredText(attributes.get("sub")),
                displayName(attributes.get("name")),
                profileImageUrl(attributes.get("picture"))
        );
    }

    private static String requiredText(Object value) {
        String text = text(value);
        if (text == null) {
            throw new IllegalArgumentException("OAuth provider user id is missing");
        }
        return text;
    }

    private static String displayName(Object value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= MAX_DISPLAY_NAME_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_DISPLAY_NAME_LENGTH));
    }

    private static String profileImageUrl(Object value) {
        String text = text(value);
        return text != null && text.length() <= MAX_PROFILE_IMAGE_URL_LENGTH ? text : null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private static Map<String, Object> nestedMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> nested = new HashMap<>();
        map.forEach((key, nestedValue) -> {
            if (key instanceof String stringKey && nestedValue != null) {
                nested.put(stringKey, nestedValue);
            }
        });
        return nested;
    }
}
