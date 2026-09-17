package com.yufesta.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.security.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenProviderTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant ISSUED_AT = Instant.parse("2026-10-08T03:00:00Z");
    private static final AuthProperties.Jwt PROPERTIES =
            new AuthProperties.Jwt("test-secret-must-be-at-least-32-bytes-long", Duration.ofHours(2));

    private final JwtTokenProvider jwtTokenProvider = providerAt(ISSUED_AT);

    @Test
    void userId를_담은_토큰을_발급하고_검증한다() {
        String token = jwtTokenProvider.createAccessToken(1L);

        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(1L);

        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                StandardCharsets.UTF_8
        );
        assertThat(payload)
                .contains("\"uid\":1", "\"iat\":" + ISSUED_AT.getEpochSecond(), "\"exp\"")
                .doesNotContain("role", "provider", "displayName");
    }

    @Test
    void 유효기간_안에서는_검증을_통과하고_지나면_JwtException이다() {
        String token = jwtTokenProvider.createAccessToken(1L);

        assertThat(providerAt(ISSUED_AT.plus(Duration.ofMinutes(119))).getUserId(token)).isEqualTo(1L);
        assertThatThrownBy(() -> providerAt(ISSUED_AT.plus(Duration.ofHours(3))).getUserId(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 유효하지_않은_토큰은_JwtException이다() {
        assertThatThrownBy(() -> jwtTokenProvider.getUserId("invalid-token"))
                .isInstanceOf(JwtException.class);
    }

    // 같은 비밀키, 다른 "지금" 시각의 provider
    private static JwtTokenProvider providerAt(Instant now) {
        return new JwtTokenProvider(PROPERTIES, Clock.fixed(now, KST));
    }
}
