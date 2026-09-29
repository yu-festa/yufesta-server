package com.yufesta.domain.match.event;

import com.yufesta.domain.match.enums.RoundStatus;

/**
 * 회차에 일어나는 일. 각각이 회차를 어떤 상태로 만드는지와 SSE 이벤트 이름을 함께 가진다
 */
public enum RoundEventType {
    OPENED("round-opened", RoundStatus.OPEN),
    CLOSED("round-closed", RoundStatus.CLOSED),
    PUBLISHED("round-published", RoundStatus.PUBLISHED);

    private final String eventName;
    private final RoundStatus resultingStatus;

    RoundEventType(String eventName, RoundStatus resultingStatus) {
        this.eventName = eventName;
        this.resultingStatus = resultingStatus;
    }

    public String eventName() {
        return eventName;
    }

    public RoundStatus resultingStatus() {
        return resultingStatus;
    }
}
