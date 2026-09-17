package com.yufesta.common.security.config;

import com.yufesta.common.security.handler.SecurityErrorResponseHandler;
import com.yufesta.common.security.jwt.AuthCookieService;
import com.yufesta.common.security.jwt.JwtAuthenticationFilter;
import com.yufesta.common.security.jwt.JwtTokenProvider;
import com.yufesta.common.security.oauth2.OAuth2LoginSuccessHandler;
import com.yufesta.common.security.oauth2.OAuth2ProviderUserIdExtractor;
import com.yufesta.common.security.oauth2.OAuth2RedirectRequestResolver;
import com.yufesta.domain.user.repository.UserRepository;
import com.yufesta.domain.user.service.UserLoginService;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;

/**
 * URL별 인가 규칙, 401·403 JSON 응답, CORS, CSRF, OAuth 로그인, 쿠키 JWT 인증 필터를 구성
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private static final String OWNER = "OWNER";
    private static final String STAFF = "STAFF";

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
            AuthProperties authProperties,
            ObjectMapper objectMapper
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
        SecurityErrorResponseHandler errorResponseHandler = new SecurityErrorResponseHandler(objectMapper);

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                // OAuth state 검증에 세션이 필요하므로 IF_REQUIRED 유지. 로그인 성공 뒤 세션은 핸들러가 제거
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                // API 서버라 401 요청을 세션에 저장해 두었다가 되돌려 줄 일이 없음
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(errorResponseHandler)
                        .accessDeniedHandler(errorResponseHandler)
                )
                // 먼저 매칭되는 규칙이 이기므로 순서를 바꾸지 말 것 (CLAUDE.md 7장)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/**",
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(
                                "/api/v1/admin/match/rounds/*/publish",
                                "/api/v1/admin/settings/**"
                        ).hasRole(OWNER)
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(STAFF, OWNER)
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/cheers").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/cheers/*").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
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
                .build();
    }
}
