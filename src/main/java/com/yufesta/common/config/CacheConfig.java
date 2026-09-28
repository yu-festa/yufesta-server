package com.yufesta.common.config;

import com.yufesta.common.cache.CacheProperties;
import com.yufesta.common.cache.ResponseCache;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 응답 캐시 빈 등록. StringRedisTemplate은 Boot가 자동 구성한다(spring.data.redis.*).
 * 캐시가 꺼져 있어도 빈은 만든다. ResponseCache가 스스로 우회하므로 호출 측에 분기를 두지 않기 위해서다.
 * 커넥션 수명(기동 예열·유휴 유지)은 CacheConnectionKeeper가 맡는다
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public ResponseCache responseCache(StringRedisTemplate redisTemplate, CacheProperties properties, Clock clock) {
        return new ResponseCache(redisTemplate, properties, clock);
    }
}
