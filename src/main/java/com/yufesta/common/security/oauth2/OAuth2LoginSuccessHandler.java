package com.yufesta.common.security.oauth2;

import com.yufesta.common.security.jwt.AuthCookieService;
import com.yufesta.common.security.jwt.JwtTokenProvider;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.service.UserLoginService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

/**
 * 소셜 로그인 성공 후 사용자를 처리하고 JWT 쿠키를 발급
 */
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserLoginService userLoginService;
    private final OAuth2ProviderUserIdExtractor providerUserIdExtractor;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookieService;

    public OAuth2LoginSuccessHandler(
            UserLoginService userLoginService,
            OAuth2ProviderUserIdExtractor providerUserIdExtractor,
            JwtTokenProvider jwtTokenProvider,
            AuthCookieService authCookieService
    ) {
        this.userLoginService = userLoginService;
        this.providerUserIdExtractor = providerUserIdExtractor;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authCookieService = authCookieService;
    }

    @Override
    // 소셜 로그인 성공 후 사용자 처리, 쿠키 발급, 원래 경로 이동을 수행
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauth2Authentication = (OAuth2AuthenticationToken) authentication;
        OAuthProvider provider = OAuthProvider.valueOf(
                oauth2Authentication.getAuthorizedClientRegistrationId().toUpperCase()
        );
        String providerUserId = providerUserIdExtractor.extract(provider, oauth2Authentication.getPrincipal());
        User user = userLoginService.login(provider, providerUserId);

        authCookieService.addAccessToken(response, jwtTokenProvider.createAccessToken(user.getId()));

        String redirectUri = OAuth2RedirectRequestResolver.consumeRedirectUri(request);
        HttpSessionCleaner.invalidate(request);
        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }
}
