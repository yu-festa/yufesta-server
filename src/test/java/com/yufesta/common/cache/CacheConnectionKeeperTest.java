package com.yufesta.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 커넥션 수명 관리의 두 가지를 고정한다: 기동 예열은 한 번 실패해도 포기하지 않는다,
 * 끝내 실패해도 예외가 기동을 막지 않는다
 */
@ExtendWith(MockitoExtension.class)
class CacheConnectionKeeperTest {

    @Mock
    private ResponseCache cache;

    // 테스트가 재시도 간격만큼 멈추지 않도록 간격을 0으로 준다
    private CacheConnectionKeeper keeper() {
        return new CacheConnectionKeeper(cache, Duration.ZERO);
    }

    @Test
    void 예열은_첫_시도가_실패해도_붙을_때까지_다시_시도한다() {
        // 운영에서 관측한 모습: 기동 직후 DNS·핸드셰이크가 느려 처음 두 번이 실패하고 곧 붙는다
        when(cache.ping()).thenReturn(false, false, true);

        keeper().warmUp();

        verify(cache, times(3)).ping();
    }

    @Test
    void 예열이_끝내_실패해도_예외를_던지지_않는다() {
        when(cache.ping()).thenReturn(false);

        keeper().warmUp();   // 예외가 새면 기동이 막힌다

        verify(cache, times(5)).ping();
    }

    @Test
    void 스프링이_생성자를_고를_수_있어야_한다() {
        // 생성자가 둘인데 @Autowired 표시가 없으면 컨텍스트 기동이 통째로 실패한다.
        // test 프로필은 캐시를 꺼 두어(app.cache.enabled=false) 이 빈을 만들지 않으므로 여기서 따로 확인한다
        new ApplicationContextRunner()
                .withBean(ResponseCache.class, () -> cache)
                .withConfiguration(UserConfigurations.of(CacheConnectionKeeper.class))
                .run(context -> assertThat(context).hasSingleBean(CacheConnectionKeeper.class));
    }

    @Test
    void 주기_확인은_붙어_있으면_추가_동작이_없다() {
        when(cache.ping()).thenReturn(true);

        keeper().keepAlive();

        verify(cache).ping();
    }
}
