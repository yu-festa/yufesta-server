package com.yufesta.common.security.jwt;

import com.yufesta.domain.user.enums.UserRole;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 로그인 사용자를 Spring Security 인증 객체로 변환. principal은 userId(Long), 권한은 ROLE_{role}
 */
public final class LoginAuthentication {

    private static final String ROLE_PREFIX = "ROLE_";

    private LoginAuthentication() {
    }

    public static UsernamePasswordAuthenticationToken of(Long userId, UserRole role) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()))
        );
    }
}
