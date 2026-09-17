package com.yufesta.support;

import com.yufesta.domain.user.enums.UserRole;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 테스트에서 쿠키 JWT 인증과 같은 형태(principal = userId, 권한 = ROLE_{role})의 로그인 상태를 만든다
 */
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockLoginUserSecurityContextFactory.class)
public @interface WithMockLoginUser {

    long id() default 1L;

    UserRole role() default UserRole.USER;
}
