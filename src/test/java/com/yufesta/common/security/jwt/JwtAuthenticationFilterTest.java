package com.yufesta.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_쿠키면_userId를_principal로_인증한다() throws Exception {
        User user = User.builder().provider(OAuthProvider.KAKAO).providerUserId("12345").role(UserRole.STAFF).loginAt(LocalDateTime.of(2026, 10, 8, 12, 0)).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        filter().doFilter(requestWithCookie("valid"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_STAFF");
    }

    @Test
    void 위조되거나_만료된_토큰은_익명으로_통과하고_DB를_조회하지_않는다() throws Exception {
        when(jwtTokenProvider.getUserId("broken")).thenThrow(new BadJwtException("expired"));
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(requestWithCookie("broken"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    void DB_장애는_삼키지_않고_전파한다() {
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRepository.findById(7L)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> filter().doFilter(
                requestWithCookie("valid"), new MockHttpServletResponse(), new MockFilterChain()
        )).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userRepository, "access_token");
    }

    private static MockHttpServletRequest requestWithCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setCookies(new Cookie("access_token", token));
        return request;
    }
}
