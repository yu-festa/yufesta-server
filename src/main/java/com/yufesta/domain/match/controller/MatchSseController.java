package com.yufesta.domain.match.controller;

import com.yufesta.common.sse.SseConnectionRegistry;
import com.yufesta.domain.match.controller.api.MatchSseApi;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 인스타팅 상태 SSE. 명세는 MatchSseApi
 */
@RestController
public class MatchSseController implements MatchSseApi {

    private final SseConnectionRegistry registry;

    public MatchSseController(SseConnectionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public SseEmitter subscribe(Long userId) {
        return registry.register(userId != null);
    }
}
