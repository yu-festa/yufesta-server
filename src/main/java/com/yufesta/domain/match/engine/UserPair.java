package com.yufesta.domain.match.engine;

/**
 * 방향 없는 회원 쌍. 차단·이전 회차 매칭처럼 "이 둘은 붙이지 않는다"를 표현한다
 */
public record UserPair(Long lowerUserId, Long higherUserId) {

    public static UserPair of(Long a, Long b) {
        return a <= b ? new UserPair(a, b) : new UserPair(b, a);
    }
}
