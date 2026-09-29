package com.yufesta.common.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 연결의 일생을 고정한다: 등록, 상한, 끝난 연결 정리, 종료 시 닫기
 */
class SseConnectionRegistryTest {

    private SseConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        // 전송을 호출한 스레드에서 바로 실행해 결과를 곧바로 본다
        registry = new SseConnectionRegistry(properties(2), Runnable::run);
        registry.start();
    }

    @Test
    void 연결하면_목록에_들어간다() {
        registry.register(false);
        registry.register(true);

        assertThat(registry.connectionCount()).isEqualTo(2);
    }

    @Test
    void 상한을_넘는_연결은_받지_않는다() {
        registry.register(false);
        registry.register(false);

        assertThatThrownBy(() -> registry.register(false))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SSE_UNAVAILABLE);
    }

    @Test
    void 끝난_연결에_보내면_목록에서_빠진다() {
        SseEmitter emitter = registry.register(false);
        emitter.complete();   // 브라우저가 닫은 상황. 이후 쓰기는 실패한다

        registry.broadcast("round-closed", "{\"roundSeq\":1}", false);

        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    void 빈_메시지_전송도_끝난_연결을_걸러낸다() {
        registry.register(false).complete();

        registry.keepAlive();

        assertThat(registry.connectionCount()).isZero();
    }

    @Test
    void 종료하면_연결을_모두_닫고_새_연결을_받지_않는다() {
        registry.register(false);
        registry.register(true);

        registry.stop();

        assertThat(registry.connectionCount()).isZero();
        assertThat(registry.isRunning()).isFalse();
        assertThatThrownBy(() -> registry.register(false)).isInstanceOf(CustomException.class);
    }

    private static SseProperties properties(int maxConnections) {
        return new SseProperties(maxConnections, Duration.ofSeconds(25), Duration.ofMinutes(30), false);
    }
}
