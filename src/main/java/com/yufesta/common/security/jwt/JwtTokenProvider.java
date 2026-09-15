package com.yufesta.common.security.jwt;

import com.yufesta.common.security.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.util.Assert;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * JWT를 발급하고 검증
 */
public class JwtTokenProvider {

    private static final String USER_ID_CLAIM = "uid";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthProperties.Jwt properties;

    public JwtTokenProvider(AuthProperties.Jwt properties) {
        Assert.hasText(properties.secret(), "JWT_SECRET must not be empty");
        Assert.isTrue(
                properties.secret().getBytes(StandardCharsets.UTF_8).length >= 32,
                "JWT_SECRET must be at least 32 bytes"
        );

        SecretKey secretKey = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        this.properties = properties;
    }

    // 사용자 ID를 담은 JWT 발급
    public String createAccessToken(Long userId) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenValidity()))
                .claim(USER_ID_CLAIM, userId)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // JWT에서 사용자 ID를 꺼내 검증
    public Long getUserId(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        Object claim = jwt.getClaim(USER_ID_CLAIM);
        if (!(claim instanceof Number number)) {
            throw new BadJwtException("JWT uid claim is missing");
        }
        return number.longValue();
    }

    // JWT 만료 시간을 쿠키의 Max-Age 값으로 반환
    public long getAccessTokenMaxAgeSeconds() {
        return properties.accessTokenValidity().toSeconds();
    }
}
