package com.yufesta.support;

import com.yufesta.common.security.jwt.LoginAuthentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * WithMockLoginUser 어노테이션 값으로 SecurityContext를 채운다
 */
public class WithMockLoginUserSecurityContextFactory implements WithSecurityContextFactory<WithMockLoginUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockLoginUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(LoginAuthentication.of(annotation.id(), annotation.role()));
        return context;
    }
}
