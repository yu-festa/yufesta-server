package com.yufesta.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class CheerRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AppSettingReader appSettingReader;

    private CheerRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new CheerRateLimiter(redisTemplate, appSettingReader);
        when(appSettingReader.getInt(SettingKey.RATELIMIT_CHEER_ANON_PER_MINUTE)).thenReturn(1);
        when(appSettingReader.getInt(SettingKey.RATELIMIT_CHEER_IP_PER_MINUTE)).thenReturn(10);
    }

    @Test
    void 익명키와_IP_한도에_여유가_있으면_작성_슬롯을_확보한다() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        rateLimiter.check("writer-hash", "203.0.113.10");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void 둘_중_하나라도_분당_한도를_넘으면_RATE_LIMITED를_반환한다() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(0L);

        assertThatThrownBy(() -> rateLimiter.check("writer-hash", "203.0.113.10"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void Redis_장애는_응원_작성_장애로_번지지_않는다() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));

        rateLimiter.check("writer-hash", "203.0.113.10");
    }

    @Test
    void 잘못된_한도_설정은_Redis를_호출하지_않고_설정_오류로_처리한다() {
        when(appSettingReader.getInt(SettingKey.RATELIMIT_CHEER_ANON_PER_MINUTE)).thenReturn(0);

        assertThatThrownBy(() -> rateLimiter.check("writer-hash", "203.0.113.10"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APP_SETTING_INVALID);
        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }
}
