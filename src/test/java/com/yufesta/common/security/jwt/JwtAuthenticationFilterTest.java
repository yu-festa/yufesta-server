package com.yufesta.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yufesta.domain.user.enums.UserRole;
import jakarta.servlet.http.Cookie;
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

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRoleCache userRoleCache;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_쿠키면_userId를_principal로_인증한다() throws Exception {
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRoleCache.find(7L)).thenReturn(Optional.of(UserRole.USER));

        filter().doFilter(request("/api/v1/auth/me", "valid"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void 운영자_경로는_기억해_둔_값을_쓰지_않고_DB에서_역할을_읽는다() throws Exception {
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRoleCache.loadFresh(7L)).thenReturn(Optional.of(UserRole.STAFF));

        for (String path : new String[] {"/api/v1/admin/match/rounds", "/api/v1/admin", "/actuator/metrics"}) {
            SecurityContextHolder.clearContext();
            filter().doFilter(request(path, "valid"), new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority").containsExactly("ROLE_STAFF");
        }
        // 역할 회수·부여가 운영자 권한에는 즉시 반영돼야 한다
        verify(userRoleCache, never()).find(7L);
    }

    @Test
    void 이름만_비슷한_경로는_운영자_경로가_아니다() throws Exception {
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRoleCache.find(7L)).thenReturn(Optional.of(UserRole.USER));

        filter().doFilter(request("/api/v1/administrators", "valid"), new MockHttpServletResponse(), new MockFilterChain());

        verify(userRoleCache, never()).loadFresh(7L);
    }

    @Test
    void 회원이_없으면_익명으로_통과한다() throws Exception {
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRoleCache.find(7L)).thenReturn(Optional.empty());
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("/api/v1/auth/me", "valid"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 위조되거나_만료된_토큰은_익명으로_통과하고_DB를_조회하지_않는다() throws Exception {
        when(jwtTokenProvider.getUserId("broken")).thenThrow(new BadJwtException("expired"));
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(request("/api/v1/auth/me", "broken"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(userRoleCache);
    }

    @Test
    void DB_장애는_삼키지_않고_전파한다() {
        when(jwtTokenProvider.getUserId("valid")).thenReturn(7L);
        when(userRoleCache.find(7L)).thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatThrownBy(() -> filter().doFilter(
                request("/api/v1/auth/me", "valid"), new MockHttpServletResponse(), new MockFilterChain()
        )).isInstanceOf(DataAccessResourceFailureException.class);
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userRoleCache, "access_token");
    }

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setCookies(new Cookie("access_token", token));
        return request;
    }
}
