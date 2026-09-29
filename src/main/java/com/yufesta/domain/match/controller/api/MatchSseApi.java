package com.yufesta.domain.match.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Match SSE", description = "인스타팅 회차 상태 실시간 알림")
@RequestMapping("/api/v1/sse")
public interface MatchSseApi {

    @Operation(
            summary = "인스타팅 상태 스트림",
            description = """
                    회차 상태가 바뀔 때 이벤트를 보낸다. 로그인 불필요(로그인 쿠키가 있으면 발표 이벤트도 받는다).
                    프론트는 `new EventSource(url, { withCredentials: true })`로 연결한다. Swagger에서는 응답이 끝나지 않는다.

                    | 이벤트 | 시점 | data |
                    |---|---|---|
                    | `connected` | 연결 직후 | `{}` |
                    | `round-opened` | 회차 접수 시작 | `{"roundSeq":2}` |
                    | `round-closed` | 회차 마감 | `{"roundSeq":1}` |
                    | `round-published` | 회차 발표(로그인 연결만) | `{"roundSeq":1}` |

                    이벤트는 "다시 조회하라"는 신호다. 받으면 `GET /api/v1/match/summary`(발표는 결과 API도)를 다시 부른다.
                    연결이 끊기면 브라우저가 자동으로 다시 연결하며, 놓친 이벤트는 다시 오지 않으므로
                    `connected`를 받을 때마다 요약을 한 번 조회한다. 25초마다 빈 메시지가 온다. SRS 4.3, NFR-PF-02
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "SSE_UNAVAILABLE")
    @GetMapping(value = "/match", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
