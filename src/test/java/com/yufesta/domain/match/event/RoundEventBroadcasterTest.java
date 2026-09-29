package com.yufesta.domain.match.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.yufesta.common.sse.SseConnectionRegistry;
import com.yufesta.domain.match.enums.RoundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 같은 일이 Pub/Sub와 주기 확인 두 경로로 와도 연결에는 한 번만 나가는지, 놓친 일은 순서대로 나가는지 고정한다
 */
@ExtendWith(MockitoExtension.class)
class RoundEventBroadcasterTest {

    @Mock
    private SseConnectionRegistry registry;

    @InjectMocks
    private RoundEventBroadcaster broadcaster;

    @Test
    void 같은_이벤트가_두_번_와도_한_번만_보낸다() {
        RoundEvent closed = new RoundEvent(RoundEventType.CLOSED, 1);

        assertThat(broadcaster.broadcast(closed)).isTrue();    // Pub/Sub로 먼저 도착
        assertThat(broadcaster.broadcast(closed)).isFalse();   // 같은 메시지가 또 옴

        verify(registry, times(1)).broadcast("round-closed", "{\"roundSeq\":1}", false);
    }

    @Test
    void 발표는_로그인_연결에만_보낸다() {
        broadcaster.broadcast(new RoundEvent(RoundEventType.PUBLISHED, 1));

        verify(registry).broadcast("round-published", "{\"roundSeq\":1}", true);
    }

    @Test
    void Pub_Sub로_이미_알린_일은_주기_확인이_다시_알리지_않는다() {
        broadcaster.broadcast(new RoundEvent(RoundEventType.CLOSED, 1));

        broadcaster.catchUp(1, RoundStatus.CLOSED);

        verify(registry, times(1)).broadcast(anyString(), anyString(), anyBoolean());
    }

    @Test
    void Pub_Sub를_놓쳤으면_주기_확인이_빠진_일을_순서대로_알린다() {
        broadcaster.catchUp(1, RoundStatus.CLOSED);   // 기동 직후 기준: 1회차 마감 상태

        // 그 사이 1회차가 발표되고 2회차가 열렸는데 메시지를 받지 못했다
        broadcaster.catchUp(2, RoundStatus.OPEN);

        InOrder order = inOrder(registry);
        order.verify(registry).broadcast("round-published", "{\"roundSeq\":1}", true);
        order.verify(registry).broadcast("round-opened", "{\"roundSeq\":2}", false);
    }

    @Test
    void 기동_직후_첫_확인은_기준만_잡고_알리지_않는다() {
        broadcaster.catchUp(2, RoundStatus.OPEN);

        verify(registry, never()).broadcast(anyString(), anyString(), anyBoolean());
    }

    @Test
    void 늦게_도착한_앞선_이벤트는_버린다() {
        broadcaster.broadcast(new RoundEvent(RoundEventType.OPENED, 2));

        // 1회차 발표 메시지가 순서가 바뀌어 늦게 왔다
        assertThat(broadcaster.broadcast(new RoundEvent(RoundEventType.PUBLISHED, 1))).isFalse();
    }

    @Test
    void 회차가_초기화되면_기준을_다시_잡고_이후_변화를_알린다() {
        broadcaster.catchUp(2, RoundStatus.PUBLISHED);   // 리허설이 끝난 상태
        broadcaster.catchUp(1, RoundStatus.OPEN);        // 운영자가 초기화했다

        broadcaster.catchUp(1, RoundStatus.CLOSED);

        verify(registry, times(1)).broadcast("round-closed", "{\"roundSeq\":1}", false);
    }

    @Test
    void 채널_메시지는_보낸_그대로_복원된다() {
        RoundEvent event = new RoundEvent(RoundEventType.PUBLISHED, 2);

        assertThat(event.encode()).isEqualTo("PUBLISHED:2");
        assertThat(RoundEvent.decode("PUBLISHED:2")).isEqualTo(event);
    }
}
