package com.yufesta.domain.match.event;

/**
 * 회차 상태가 바뀐 것을 알린다. 서비스는 "알린다"만 알고, 어떻게 모든 태스크의 연결에 닿는지는 구현이 맡는다.
 * 지금 구현은 Redis Pub/Sub 하나다({@link RedisRoundEventPublisher})
 */
public interface RoundEventPublisher {

    /** 트랜잭션 안에서 부르면 커밋된 뒤에 나간다. 롤백되면 나가지 않는다 */
    void publish(RoundEvent event);
}
