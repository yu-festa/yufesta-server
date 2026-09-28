package com.yufesta.common.security.oauth2;

/** 소셜 로그인 제공자가 반환한 최소 프로필 정보. 이메일은 수집하지 않는다. */
public record OAuth2UserProfile(
        String providerUserId,
        String displayName,
        String profileImageUrl
) {
}
