package com.yufesta.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.http.MediaType;

/**
 * 응답 캐시 필터가 (1) 대상 경로만 캐시하고 (2) 적중 시 컨트롤러를 타지 않고 (3) 캐시하면 안 되는 응답을 저장하지 않는지 확인
 */
@ExtendWith(MockitoExtension.class)
class CachedResponseFilterTest {

    @Mock
    private ResponseCache cache;

    @Test
    void 대상_경로가_아니면_캐시를_건드리지_않는다() throws Exception {
        CachedResponseFilter filter = new CachedResponseFilter(cache);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/cheers");

        // 응원 메시지는 isMine이 사용자마다 달라 캐시 대상이 아니다
        assertThat(filter.shouldNotFilter(request)).isTrue();
        // 쓰기 요청도 제외
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/notices"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/timetable"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/notices/banner"))).isFalse();
    }

    @Test
    void 캐시_미스면_컨트롤러를_태우고_본문을_저장한다() throws Exception {
        // ResponseCache가 loader를 실행하는 실제 동작을 흉내 낸다
        when(cache.get(anyString(), any(Duration.class), any())).thenAnswer(invocation ->
                invocation.<Supplier<String>>getArgument(2).get());
        CachedResponseFilter filter = new CachedResponseFilter(cache);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/timetable");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger controllerCalls = new AtomicInteger();

        filter.doFilter(request, response, jsonChain(controllerCalls, "{\"slots\":[]}", HttpServletResponse.SC_OK));

        assertThat(controllerCalls).hasValue(1);
        assertThat(response.getContentAsString()).isEqualTo("{\"slots\":[]}");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache).get(key.capture(), any(Duration.class), any());
        assertThat(key.getValue()).isEqualTo("http:/api/v1/timetable");
    }

    @Test
    void 캐시_적중이면_컨트롤러를_타지_않고_저장된_본문을_그대로_내려준다() throws Exception {
        when(cache.get(anyString(), any(Duration.class), any())).thenReturn("{\"cached\":true}");
        CachedResponseFilter filter = new CachedResponseFilter(cache);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger controllerCalls = new AtomicInteger();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/clubs"), response,
                jsonChain(controllerCalls, "여기까지 오면 안 된다", HttpServletResponse.SC_OK));

        assertThat(controllerCalls).hasValue(0);
        assertThat(response.getContentAsString()).isEqualTo("{\"cached\":true}");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void 질의_파라미터는_허용_목록만_키에_넣는다() throws Exception {
        when(cache.get(anyString(), any(Duration.class), any())).thenReturn("{}");
        CachedResponseFilter filter = new CachedResponseFilter(cache);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        // size·category는 응답을 가르므로 키에 들어가고, 모르는 파라미터(tracking)는 버린다.
        // 그래야 누구나 ?x=1,2,3...으로 키를 무한히 만들지 못한다
        request.setQueryString("tracking=abc&category=STAGE");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(cache).get(key.capture(), any(Duration.class), any());
        assertThat(key.getValue()).isEqualTo("http:/api/v1/places:category=STAGE");
    }

    @Test
    void 오류_응답과_쿠키가_붙은_응답은_저장하지_않는다() throws Exception {
        CachedResponseFilter filter = new CachedResponseFilter(cache);

        // 404를 캐시하면 잠깐의 오류가 TTL 동안 굳는다 → loader가 null을 돌려줘야 한다
        assertThat(capturedBody(filter, "/api/v1/notices/999", HttpServletResponse.SC_NOT_FOUND, false)).isNull();
        // Set-Cookie가 붙은 응답은 사용자별 상태를 담는다 → 남에게 나눠 주면 안 된다
        assertThat(capturedBody(filter, "/api/v1/notices", HttpServletResponse.SC_OK, true)).isNull();
        // 정상 200은 저장 대상
        assertThat(capturedBody(filter, "/api/v1/notices", HttpServletResponse.SC_OK, false)).isEqualTo("{\"ok\":1}");
    }

    /** loader가 무엇을 돌려주는지(= 캐시에 저장될 값) 확인한다 */
    private String capturedBody(CachedResponseFilter filter, String uri, int status, boolean withCookie)
            throws Exception {
        String[] captured = {"미실행"};
        when(cache.get(anyString(), any(Duration.class), any())).thenAnswer(invocation -> {
            captured[0] = invocation.<Supplier<String>>getArgument(2).get();
            return captured[0];
        });
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                HttpServletResponse http = (HttpServletResponse) res;
                http.setStatus(status);
                http.setContentType("application/json");
                if (withCookie) {
                    http.setHeader("Set-Cookie", "anon_key=abc");
                }
                res.getOutputStream().write("{\"ok\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        filter.doFilter(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse(), chain);
        return captured[0];
    }

    private static MockFilterChain jsonChain(AtomicInteger calls, String body, int status) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException {
                calls.incrementAndGet();
                HttpServletResponse http = (HttpServletResponse) res;
                http.setStatus(status);
                http.setContentType("application/json");
                res.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
    }
}
