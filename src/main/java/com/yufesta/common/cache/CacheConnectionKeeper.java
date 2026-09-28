package com.yufesta.common.cache;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 커넥션 하나를 살아 있는 상태로 유지한다. 캐시 <b>내용</b>이 아니라 <b>연결</b>만 다룬다.
 *
 * <p>Redis 커넥션은 HTTP와 달리 요청마다 맺지 않고 하나를 계속 재사용한다. 그래서 연결이 없거나 끊긴
 * 그 순간의 요청 하나만 캐시를 건너뛰는데, 운영에서 두 경우 모두 실제로 관측됐다.
 * <ol>
 *   <li><b>기동 직후</b>(2026-09-27) — 태스크 두 개 모두 기동 3.3~3.5초 뒤 첫 연결에 실패했다.
 *       DNS 조회 + TCP 핸드셰이크를 해야 하는데 그 시각 CPU는 Spring 초기화가 쓰고 있다.
 *       한 번만 시도하고 포기하면 예열이 아무 일도 하지 않은 것과 같아 {@link #WARM_UP_ATTEMPTS}번 다시 두드린다</li>
 *   <li><b>장시간 유휴</b>(2026-09-28 08:49) — 전날 저녁부터 트래픽이 0이라 소켓이 정리됐고,
 *       아침 첫 요청이 끊긴 소켓에 명령을 보내 실패했다. {@link #KEEP_ALIVE_MILLIS}마다 말을 걸어 막는다</li>
 * </ol>
 *
 * <p>어느 쪽도 사용자 영향은 없다(fail-open으로 DB 경로를 타고 200을 준다). 고치는 이유는 캐시 적중률과
 * 로그 신뢰성이다. 캐시를 끄면({@code app.cache.enabled=false}) 이 빈 자체를 만들지 않는다.
 */
@Component
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConnectionKeeper {

    private static final Logger log = LoggerFactory.getLogger(CacheConnectionKeeper.class);

    private static final int WARM_UP_ATTEMPTS = 5;
    private static final Duration WARM_UP_GAP = Duration.ofMillis(500);
    private static final long KEEP_ALIVE_MILLIS = 60_000;

    private final ResponseCache cache;
    private final Duration warmUpGap;
    /** 끊김·복구가 바뀌는 순간에만 로그를 남기려는 상태. 매 분 경고를 쌓지 않기 위해서다 */
    private final AtomicBoolean connected = new AtomicBoolean(true);

    // 생성자가 둘이라 스프링에게 어느 쪽을 쓸지 알려 준다. 아래 것은 재시도 간격을 줄이는 테스트 전용이다
    @Autowired
    public CacheConnectionKeeper(ResponseCache cache) {
        this(cache, WARM_UP_GAP);
    }

    CacheConnectionKeeper(ResponseCache cache, Duration warmUpGap) {
        this.cache = cache;
        this.warmUpGap = warmUpGap;
    }

    /**
     * 기동이 끝나면 커넥션을 맺는다. 성공할 때까지 짧은 간격으로 다시 시도하고, 끝내 실패해도 기동을 막지 않는다.
     * <p>{@code ApplicationReadyEvent}는 웹 서버가 요청을 받기 시작한 뒤에 오므로 여기서 기다려도 응답이 밀리지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        for (int attempt = 1; attempt <= WARM_UP_ATTEMPTS; attempt++) {
            if (cache.ping()) {
                log.info("응답 캐시 연결 준비 완료 ({}회 시도)", attempt);
                return;
            }
            if (!pause()) {
                return;
            }
        }
        connected.set(false);
        log.warn("응답 캐시 연결 준비 실패. 첫 요청이 캐시를 건너뛸 수 있다(응답은 정상)");
    }

    /** 유휴 커넥션이 끊기지 않게 주기적으로 명령 하나를 보낸다. 끊김·복구가 바뀔 때만 로그를 남긴다 */
    @Scheduled(fixedDelay = KEEP_ALIVE_MILLIS, initialDelay = KEEP_ALIVE_MILLIS)
    public void keepAlive() {
        if (cache.ping()) {
            if (connected.compareAndSet(false, true)) {
                log.info("응답 캐시 연결 복구");
            }
            return;
        }
        if (connected.compareAndSet(true, false)) {
            log.warn("응답 캐시 연결이 끊겼다. 캐시를 건너뛰고 DB로 응답한다");
        }
    }

    // 기다리는 중에 종료 신호가 오면 더 붙들지 않는다
    private boolean pause() {
        try {
            Thread.sleep(warmUpGap.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
