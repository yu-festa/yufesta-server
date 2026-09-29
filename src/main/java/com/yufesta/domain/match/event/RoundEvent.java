package com.yufesta.domain.match.event;

import com.yufesta.domain.match.enums.RoundStatus;

/**
 * "몇 회차에 무슨 일이 있었다"는 신호. 매칭 결과 같은 내용은 싣지 않는다.
 * 받은 쪽(브라우저)은 기존 조회 API를 다시 부르고, 권한 검사는 그 API가 한다
 *
 * <h2>진행 순번({@link #position})</h2>
 * 회차는 앞으로만 간다: 1회차 접수 → 1회차 마감 → 1회차 발표 → 2회차 접수 → …
 * 이 순서를 숫자 하나로 나타내면(회차 × 10 + 상태 순서) "이미 알린 일인가"를 크기 비교로 판단할 수 있다.
 * <pre>
 *   1회차 접수 11 → 1회차 마감 12 → 1회차 발표 13 → 2회차 접수 21 → 2회차 마감 22 → 2회차 발표 23
 * </pre>
 * 같은 일이 두 경로(Pub/Sub, 주기 확인)로 올 수 있어 필요하다. {@link RoundEventBroadcaster} 참고
 */
public record RoundEvent(RoundEventType type, int roundSeq) {

    private static final String SEPARATOR = ":";

    public int position() {
        return positionOf(roundSeq, type.resultingStatus());
    }

    public static int positionOf(int roundSeq, RoundStatus status) {
        return roundSeq * 10 + status.ordinal();
    }

    /** 브라우저로 보내는 본문 */
    public String toJson() {
        return "{\"roundSeq\":" + roundSeq + "}";
    }

    /** Redis 채널로 보내는 형태. 예: PUBLISHED:1 */
    public String encode() {
        return type.name() + SEPARATOR + roundSeq;
    }

    /** @throws IllegalArgumentException 형식이 다를 때 */
    public static RoundEvent decode(String message) {
        String[] parts = message.split(SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalArgumentException("회차 이벤트 형식이 아니다: " + message);
        }
        return new RoundEvent(RoundEventType.valueOf(parts[0]), Integer.parseInt(parts[1]));
    }
}
