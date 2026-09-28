package com.yufesta.common.ratelimit;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 로그인 회원의 분실물 게시글 작성 횟수를 Redis에서 분당 제한한다. */
@Service
public class LostItemRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LostItemRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>();

    static {
        RESERVE_SCRIPT.setScriptText("""
                local count = tonumber(redis.call('GET', KEYS[1]) or '0')
                if count >= tonumber(ARGV[1]) then return 0 end

                local newCount = redis.call('INCR', KEYS[1])
                if newCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
                return 1
                """);
        RESERVE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final AppSettingReader appSettingReader;

    public LostItemRateLimiter(StringRedisTemplate redisTemplate, AppSettingReader appSettingReader) {
        this.redisTemplate = redisTemplate;
        this.appSettingReader = appSettingReader;
    }

    /** 설정된 회원당 분당 한도 안일 때만 게시글 작성 슬롯을 확보한다. */
    public void check(Long userId) {
        int limit = appSettingReader.getInt(SettingKey.RATELIMIT_LOSTITEM_PER_MINUTE);
        if (limit < 1) {
            throw new CustomException(ErrorCode.APP_SETTING_INVALID);
        }

        try {
            Long reserved = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(userKey(userId)),
                    Integer.toString(limit),
                    Long.toString(WINDOW.toSeconds())
            );
            if (Long.valueOf(0L).equals(reserved)) {
                throw new CustomException(ErrorCode.RATE_LIMITED);
            }
            if (reserved == null) {
                log.warn("분실물 작성 속도 제한 Redis 응답이 비어 있어 제한을 우회합니다");
            }
        } catch (DataAccessException exception) {
            log.warn("분실물 작성 속도 제한 Redis 접근 실패, 제한을 우회합니다: {}", exception.getClass().getSimpleName());
        }
    }

    private static String userKey(Long userId) {
        return "yufesta:ratelimit:lostitem:user:" + userId;
    }
}
