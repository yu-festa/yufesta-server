package com.yufesta.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void 요청_완료_후_상태_코드와_처리_시간을_기록한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/festivals");
        request.setQueryString("token=secret");
        request.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        requestLoggingFilter.doFilter(request, response, (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(200);
        });

        String message = logAppender.list.get(0).getFormattedMessage();
        assertThat(message).contains("HTTP GET /api/v1/festivals -> 200", "ms");
        assertThat(message).doesNotContain("token=secret", "secret-token");
    }

    @Test
    void 예외가_발생해도_응답_상태와_처리_시간을_기록한다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/festivals");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> requestLoggingFilter.doFilter(request, response, (req, res) -> {
            ((MockHttpServletResponse) res).setStatus(500);
            throw new ServletException("test exception");
        })).isInstanceOf(ServletException.class);

        assertThat(logAppender.list.get(0).getFormattedMessage())
                .contains("HTTP POST /api/v1/festivals -> 500", "ms");
    }

    @Test
    void health_check와_문서_요청은_로그를_기록하지_않는다() throws Exception {
        assertThat(requestLoggingFilter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health")))
                .isTrue();
        assertThat(requestLoggingFilter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html")))
                .isTrue();

        AtomicBoolean filterChainCalled = new AtomicBoolean();
        requestLoggingFilter.doFilter(
                new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(),
                (req, res) -> filterChainCalled.set(true)
        );

        assertThat(filterChainCalled).isTrue();
        assertThat(logAppender.list).isEmpty();
    }
}
