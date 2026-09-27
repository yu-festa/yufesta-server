package com.yufesta.support;

import com.yufesta.common.cache.CacheProperties;
import com.yufesta.common.cache.PublicCacheEvictor;
import com.yufesta.common.cache.ResponseCache;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 슬라이스 테스트용 캐시 빈. @WebMvcTest는 Filter 빈을 함께 올리므로 CachedResponseFilter가 ResponseCache를 요구하는데,
 * 슬라이스에는 Redis 자동 구성이 없다. 여기서 <b>캐시를 끈 실제 구현</b>을 준다.
 * mock으로 대체하면 get()이 null을 돌려주며 컨트롤러를 아예 타지 않아 응답이 비어 버린다.
 * 캐시가 꺼져 있으면 ResponseCache는 Redis를 건드리지 않고 원본 계산만 실행한다(그래서 template이 null이어도 된다)
 */
@TestConfiguration
public class DisabledCacheConfig {

    @Bean
    public ResponseCache responseCache(Clock clock) {
        return new ResponseCache(null, new CacheProperties(false, "v1", null, null), clock);
    }

    @Bean
    public PublicCacheEvictor publicCacheEvictor(ResponseCache responseCache) {
        return new PublicCacheEvictor(responseCache);
    }
}
