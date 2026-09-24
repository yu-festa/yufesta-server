package com.yufesta.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 쿠키 domain 정규화(선행 '.' 제거) 확인
 */
class AuthPropertiesTest {

    @Test
    void 쿠키_domain의_선행_점을_뗀다() {
        AuthProperties.Cookie cookie = new AuthProperties.Cookie("access_token", true, ".yufesta.com");

        assertThat(cookie.domain()).isEqualTo("yufesta.com");
    }

    @Test
    void 점이_없거나_비어_있으면_그대로_둔다() {
        assertThat(new AuthProperties.Cookie("access_token", true, "yufesta.com").domain()).isEqualTo("yufesta.com");
        assertThat(new AuthProperties.Cookie("access_token", true, "").domain()).isEmpty();
        assertThat(new AuthProperties.Cookie("access_token", true, null).domain()).isNull();
    }
}
