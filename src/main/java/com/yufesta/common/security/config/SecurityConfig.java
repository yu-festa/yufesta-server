package com.yufesta.common.security.config;

import com.yufesta.common.security.jwt.AuthCookieService;
import com.yufesta.common.security.jwt.JwtAuthenticationFilter;
import com.yufesta.common.security.jwt.JwtTokenProvider;
import com.yufesta.common.security.oauth2.OAuth2LoginSuccessHandler;
import com.yufesta.common.security.oauth2.OAuth2ProviderUserIdExtractor;
import com.yufesta.common.security.oauth2.OAuth2RedirectRequestResolver;
import com.yufesta.domain.user.repository.UserRepository;
import com.yufesta.domain.user.service.UserLoginService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    // JWT 발급과 검증을 담당하는 객체를 등록
    @Bean
    public JwtTokenProvider jwtTokenProvider(AuthProperties authProperties) {
        return new JwtTokenProvider(authProperties.jwt());
    }

    // JWT를 쿠키에 담거나 삭제하는 객체를 등록
    @Bean
    public AuthCookieService authCookieService(AuthProperties authProperties, JwtTokenProvider jwtTokenProvider) {
        return new AuthCookieService(authProperties.cookie(), jwtTokenProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            UserLoginService userLoginService,
            OAuth2ProviderUserIdExtractor providerUserIdExtractor,
            JwtTokenProvider jwtTokenProvider,
            AuthCookieService authCookieService,
            UserRepository userRepository,
            AuthProperties authProperties
    ) throws Exception {
        OAuth2RedirectRequestResolver authorizationRequestResolver = new OAuth2RedirectRequestResolver(
                clientRegistrationRepository
        );
        OAuth2LoginSuccessHandler successHandler = new OAuth2LoginSuccessHandler(
                userLoginService,
                providerUserIdExtractor,
                jwtTokenProvider,
                authCookieService
        );

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/**",
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestResolver(authorizationRequestResolver)
                        )
                        .successHandler(successHandler)
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userRepository, authProperties.cookie().name()),
                        UsernamePasswordAuthenticationFilter.class
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2Client(Customizer.withDefaults())
                .build();
    }
}
