package com.yufesta.domain.cheer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.common.security.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnonymousKeyServiceTest {

    private final AnonymousKeyService anonymousKeyService = new AnonymousKeyService(new AuthProperties(
            null,
            List.of(),
            null,
            new AuthProperties.Cookie("access_token", false, "")
    ));

    @Test
    void 익명_키가_없으면_HttpOnly_쿠키를_발급한다() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String key = anonymousKeyService.resolve(new MockHttpServletRequest(), response);

        assertThat(key).isNotBlank();
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith(AnonymousKeyService.COOKIE_NAME + "=")
                .contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=2592000");
    }

    @Test
    void 기존_익명_키가_있으면_재사용하고_쿠키를_새로_발급하지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousKeyService.COOKIE_NAME, "existing-key"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String key = anonymousKeyService.resolve(request, response);

        assertThat(key).isEqualTo("existing-key");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void 익명_키는_SHA256_해시로_변환한다() {
        assertThat(anonymousKeyService.hash("anon-key"))
                .isEqualTo("d359d70ad04bba62aaab37da489af83203ef9a3d6f9a129e5dfe7b24f14ef746");
    }
}
