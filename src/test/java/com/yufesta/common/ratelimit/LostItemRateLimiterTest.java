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
class LostItemRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private AppSettingReader appSettingReader;

    private LostItemRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LostItemRateLimiter(redisTemplate, appSettingReader);
    }

    @Test
    void 회원당_분당_한도에_여유가_있으면_작성_슬롯을_확보한다() {
        when(appSettingReader.getInt(SettingKey.RATELIMIT_LOSTITEM_PER_MINUTE)).thenReturn(1);
        when(redisTemplate.execute(anyScript(), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        rateLimiter.check(7L);

        verify(redisTemplate).execute(anyScript(), anyList(), anyString(), anyString());
    }

    @Test
    void 회원당_분당_한도를_넘으면_RATE_LIMITED를_반환한다() {
        when(appSettingReader.getInt(SettingKey.RATELIMIT_LOSTITEM_PER_MINUTE)).thenReturn(1);
        when(redisTemplate.execute(anyScript(), anyList(), anyString(), anyString()))
                .thenReturn(0L);

        assertThatThrownBy(() -> rateLimiter.check(7L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    void Redis_장애는_분실물_작성_장애로_번지지_않는다() {
        when(appSettingReader.getInt(SettingKey.RATELIMIT_LOSTITEM_PER_MINUTE)).thenReturn(1);
        when(redisTemplate.execute(anyScript(), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));

        rateLimiter.check(7L);
    }

    @Test
    void 잘못된_한도_설정은_Redis를_호출하지_않고_설정_오류로_처리한다() {
        when(appSettingReader.getInt(SettingKey.RATELIMIT_LOSTITEM_PER_MINUTE)).thenReturn(0);

        assertThatThrownBy(() -> rateLimiter.check(7L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.APP_SETTING_INVALID);
        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }

    private static DefaultRedisScript<Long> anyScript() {
        return org.mockito.ArgumentMatchers.any();
    }
}
