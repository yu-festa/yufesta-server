package com.yufesta.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.security.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new AuthProperties.Jwt("test-secret-must-be-at-least-32-bytes-long", Duration.ofHours(2))
    );

    @Test
    void userId를_담은_토큰을_발급하고_검증한다() {
        String token = jwtTokenProvider.createAccessToken(1L);

        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);

        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );
        assertThat(payload)
                .contains("\"uid\":1", "\"iat\"", "\"exp\"")
                .doesNotContain("role", "provider", "displayName");
    }

    @Test
    void 유효하지_않은_토큰은_검증하지_않는다() {
        assertThatThrownBy(() -> jwtTokenProvider.getUserId("invalid-token"))
                .isInstanceOf(RuntimeException.class);
    }
}
