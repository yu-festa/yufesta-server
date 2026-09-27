package com.yufesta.common.config;

import com.yufesta.common.cache.CacheProperties;
import com.yufesta.common.cache.ResponseCache;
import java.time.Clock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 응답 캐시 빈 등록. StringRedisTemplate은 Boot가 자동 구성한다(spring.data.redis.*).
 * 캐시가 꺼져 있어도 빈은 만든다. ResponseCache가 스스로 우회하므로 호출 측에 분기를 두지 않기 위해서다
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public ResponseCache responseCache(StringRedisTemplate redisTemplate, CacheProperties properties, Clock clock) {
        return new ResponseCache(redisTemplate, properties, clock);
    }

    /**
     * 기동이 끝나면 캐시 커넥션을 미리 맺는다. ALB 헬스체크가 통과하기 전에 실행되므로
     * 첫 사용자 요청이 연결 수립 비용을 내지 않는다
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> cacheWarmUp(ResponseCache responseCache) {
        return event -> responseCache.warmUp();
    }
}
