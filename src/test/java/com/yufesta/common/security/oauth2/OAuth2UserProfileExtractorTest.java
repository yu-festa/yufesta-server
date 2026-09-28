package com.yufesta.common.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.domain.user.enums.OAuthProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

class OAuth2UserProfileExtractorTest {

    private final OAuth2UserProfileExtractor extractor = new OAuth2UserProfileExtractor();

    @Test
    void 카카오_계정의_닉네임과_프로필_사진을_추출한다() {
        DefaultOAuth2User user = oauth2User(Map.of(
                "id", 12345L,
                "kakao_account", Map.of(
                        "profile", Map.of(
                                "nickname", "카카오 사용자",
                                "profile_image_url", "https://k.kakaocdn.net/profile.jpg"
                        )
                )
        ), "id");

        OAuth2UserProfile profile = extractor.extract(OAuthProvider.KAKAO, user);

        assertThat(profile).isEqualTo(new OAuth2UserProfile(
                "12345",
                "카카오 사용자",
                "https://k.kakaocdn.net/profile.jpg"
        ));
    }

    @Test
    void 구글_계정의_이름과_프로필_사진을_추출한다() {
        DefaultOAuth2User user = oauth2User(Map.of(
                "sub", "google-user",
                "name", "Google User",
                "picture", "https://lh3.googleusercontent.com/profile"
        ), "sub");

        OAuth2UserProfile profile = extractor.extract(OAuthProvider.GOOGLE, user);

        assertThat(profile).isEqualTo(new OAuth2UserProfile(
                "google-user",
                "Google User",
                "https://lh3.googleusercontent.com/profile"
        ));
    }

    @Test
    void 선택_프로필이_없어도_제공자_식별자로_로그인할_수_있다() {
        DefaultOAuth2User user = oauth2User(Map.of("id", 12345L), "id");

        OAuth2UserProfile profile = extractor.extract(OAuthProvider.KAKAO, user);

        assertThat(profile.providerUserId()).isEqualTo("12345");
        assertThat(profile.displayName()).isNull();
        assertThat(profile.profileImageUrl()).isNull();
    }

    @Test
    void 제공자_식별자가_없으면_로그인을_거부한다() {
        DefaultOAuth2User user = oauth2User(Map.of("name", "Google User"), "name");

        assertThatThrownBy(() -> extractor.extract(OAuthProvider.GOOGLE, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OAuth provider user id is missing");
    }

    private static DefaultOAuth2User oauth2User(Map<String, Object> attributes, String nameAttributeKey) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                attributes,
                nameAttributeKey
        );
    }
}
