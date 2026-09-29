package com.yufesta.common.sse;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 이 태스크에 열려 있는 SSE 연결을 들고 있다가, 보낼 것이 생기면 전부에게 보낸다.
 *
 * <h2>연결 하나의 일생</h2>
 * <pre>
 *   register()  → 목록에 넣고 connected 이벤트를 보낸다
 *   (대기)      → 25초마다 빈 메시지. ALB가 60초 조용한 연결을 끊기 때문이다
 *   broadcast() → 이벤트가 생기면 전송
 *   끝          → 브라우저가 닫음 / 전송 실패 / 수명(30분) 만료 / 서버 종료 → 목록에서 뺀다
 * </pre>
 * 끝난 뒤에는 브라우저(EventSource)가 알아서 다시 연결한다. 서버가 재연결을 챙길 필요가 없다.
 *
 * <h2>스레드</h2>
 * {@link SseEmitter}는 서블릿 비동기 기능을 써서 연결을 열어 둔 채 요청 스레드를 돌려준다.
 * 연결 1,000개가 스레드 1,000개를 묶지 않는다. 전송은 전용 스레드 하나({@code executor})에서 차례로 한다.
 * 회차를 발표한 스레드(스케줄러·운영자 요청)가 1,000명에게 쓰느라 붙들리지 않게 하려는 것이다.
 *
 * <h2>종료</h2>
 * 열린 연결은 "진행 중인 요청"이라, 그대로 두면 서버가 끝나길 기다리다 종료 제한(30초)을 다 쓴다.
 * 그래서 종료가 시작되면 가장 먼저 연결을 닫는다({@link SmartLifecycle}의 기본 단계는 웹 서버보다 먼저 멈춘다).
 */
public class SseConnectionRegistry implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionRegistry.class);
    private static final String CONNECTED_EVENT = "connected";
    private static final String KEEP_ALIVE_COMMENT = "keepalive";

    private final SseProperties properties;
    private final Executor executor;
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private volatile boolean running;

    public SseConnectionRegistry(SseProperties properties, Executor executor) {
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * 새 연결을 등록하고 돌려준다. 컨트롤러가 이 객체를 반환하면 스프링이 연결을 열어 둔다.
     * @param loggedIn 로그인한 사용자의 연결인지. 발표 이벤트는 로그인 연결에만 보낸다
     * @throws CustomException SSE_UNAVAILABLE(연결 수 상한 초과, 서버 종료 중)
     */
    public SseEmitter register(boolean loggedIn) {
        // 상한은 메모리·소켓 보호용이다. 동시에 들어온 요청 몇 개가 상한을 살짝 넘는 것은 괜찮다
        if (!running || connections.size() >= properties.maxConnections()) {
            throw new CustomException(ErrorCode.SSE_UNAVAILABLE);
        }
        SseEmitter emitter = new SseEmitter(properties.connectionTtl().toMillis());
        Connection connection = new Connection(emitter, loggedIn);
        // 어떤 이유로 끝나든 목록에서 빠져야 한다. 남아 있으면 죽은 연결에 계속 쓰려다 실패한다
        emitter.onCompletion(() -> connections.remove(connection));
        emitter.onTimeout(() -> connections.remove(connection));
        emitter.onError(error -> connections.remove(connection));
        connections.add(connection);
        // 첫 메시지를 바로 보내 브라우저의 onopen이 확실히 불리게 한다. 프론트는 이때 요약을 한 번 조회한다
        send(connection, SseEmitter.event().name(CONNECTED_EVENT).data("{}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    /**
     * 열린 연결에 이벤트를 보낸다. 호출한 스레드는 기다리지 않는다.
     * @param loggedInOnly true면 로그인 연결에만 보낸다
     */
    public void broadcast(String eventName, String json, boolean loggedInOnly) {
        executor.execute(() -> {
            int sent = 0;
            for (Connection connection : connections) {
                if (loggedInOnly && !connection.loggedIn()) {
                    continue;
                }
                if (send(connection, SseEmitter.event().name(eventName).data(json, MediaType.APPLICATION_JSON))) {
                    sent++;
                }
            }
            log.info("SSE {} 전송: {}건 (열린 연결 {}건)", eventName, sent, connections.size());
        });
    }

    /** 조용한 연결이 중간에서 끊기지 않게 빈 메시지를 보낸다. 끊긴 연결도 이때 걸러진다 */
    @Scheduled(fixedDelayString = "${app.sse.keep-alive:PT25S}")
    public void keepAlive() {
        executor.execute(() -> {
            for (Connection connection : connections) {
                send(connection, SseEmitter.event().comment(KEEP_ALIVE_COMMENT));
            }
        });
    }

    public int connectionCount() {
        return connections.size();
    }

    // 브라우저가 이미 떠났으면 쓰기가 실패한다. 그 연결은 버린다
    private boolean send(Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
            return true;
        } catch (IOException | IllegalStateException exception) {
            connections.remove(connection);
            connection.emitter().completeWithError(exception);
            return false;
        }
    }

    @Override
    public void start() {
        running = true;
    }

    /** 새 연결을 막고 열린 연결을 닫는다. 브라우저는 곧 다시 연결을 시도하고, 배포 중이면 새 태스크에 붙는다 */
    @Override
    public void stop() {
        running = false;
        int closing = connections.size();
        for (Connection connection : connections) {
            connection.emitter().complete();
        }
        connections.clear();
        log.info("SSE 연결 {}건을 닫았다(종료)", closing);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private record Connection(SseEmitter emitter, boolean loggedIn) {
    }
}
