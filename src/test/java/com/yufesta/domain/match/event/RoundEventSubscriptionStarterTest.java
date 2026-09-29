package com.yufesta.domain.match.event;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis에 붙지 못해도 예외가 새지 않고, 붙을 때까지 다시 시도하는지 고정한다
 */
@ExtendWith(MockitoExtension.class)
class RoundEventSubscriptionStarterTest {

    @Mock
    private RedisMessageListenerContainer container;

    @InjectMocks
    private RoundEventSubscriptionStarter starter;

    @Test
    void 붙지_못하면_예외를_던지지_않고_다음에_다시_시도한다() {
        when(container.isListening()).thenReturn(false);
        doThrow(new RedisConnectionFailureException("연결 실패")).doNothing().when(container).start();

        starter.ensureListening();   // 예외가 새면 스케줄러가 이 작업을 멈춘다
        starter.ensureListening();

        verify(container, times(2)).start();
        // 실패한 컨테이너는 "시작됨" 표시가 남아 있어 멈춘 뒤에 다시 시작해야 한다
        verify(container, times(2)).stop();
    }

    @Test
    void 이미_구독_중이면_다시_시작하지_않는다() {
        when(container.isListening()).thenReturn(true);

        starter.ensureListening();

        verify(container, never()).start();
    }
}
