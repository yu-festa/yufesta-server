package com.yufesta.common.ratelimit;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 익명 응원 메시지의 익명 키·IP 단위 분당 작성 수를 Redis에서 원자적으로 제한한다. */
@Service
public class CheerRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CheerRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * 두 키를 한 Redis Lua 스크립트 안에서 확인·증가한다. 키를 따로 INCR하면 동시 요청에서
     * 둘 중 하나만 제한을 넘는 상태가 생길 수 있다.
     */
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>();

    static {
        RESERVE_SCRIPT.setScriptText("""
                local anonymousCount = tonumber(redis.call('GET', KEYS[1]) or '0')
                if anonymousCount >= tonumber(ARGV[1]) then return 0 end

                local ipCount = tonumber(redis.call('GET', KEYS[2]) or '0')
                if ipCount >= tonumber(ARGV[2]) then return 0 end

                local newAnonymousCount = redis.call('INCR', KEYS[1])
                if newAnonymousCount == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end

                local newIpCount = redis.call('INCR', KEYS[2])
                if newIpCount == 1 then redis.call('EXPIRE', KEYS[2], ARGV[3]) end
                return 1
                """);
        RESERVE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final AppSettingReader appSettingReader;

    public CheerRateLimiter(StringRedisTemplate redisTemplate, AppSettingReader appSettingReader) {
        this.redisTemplate = redisTemplate;
        this.appSettingReader = appSettingReader;
    }

    /** 익명 키와 IP 모두 설정된 분당 한도 안일 때만 작성 슬롯을 확보한다. */
    public void check(String writerKeyHash, String clientIp) {
        int anonymousLimit = appSettingReader.getInt(SettingKey.RATELIMIT_CHEER_ANON_PER_MINUTE);
        int ipLimit = appSettingReader.getInt(SettingKey.RATELIMIT_CHEER_IP_PER_MINUTE);
        validateLimit(anonymousLimit);
        validateLimit(ipLimit);

        try {
            Long reserved = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(anonymousKey(writerKeyHash), ipKey(clientIp)),
                    Integer.toString(anonymousLimit),
                    Integer.toString(ipLimit),
                    Long.toString(WINDOW.toSeconds())
            );
            if (Long.valueOf(0L).equals(reserved)) {
                throw new CustomException(ErrorCode.RATE_LIMITED);
            }
            if (reserved == null) {
                log.warn("응원 메시지 속도 제한 Redis 응답이 비어 있어 제한을 우회합니다");
            }
        } catch (DataAccessException exception) {
            // Redis는 현재 캐시와 공용 인프라다. 장애가 쓰기 API 전체 장애로 번지지 않도록 우회하고 경고를 남긴다.
            log.warn("응원 메시지 속도 제한 Redis 접근 실패, 제한을 우회합니다: {}", exception.getClass().getSimpleName());
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1) {
            throw new CustomException(ErrorCode.APP_SETTING_INVALID);
        }
    }

    private static String anonymousKey(String writerKeyHash) {
        return "yufesta:ratelimit:cheer:anon:" + writerKeyHash;
    }

    private static String ipKey(String clientIp) {
        // IP 원문은 로그·DB에 남기지 않고, Redis의 짧은 TTL 키에도 해시만 둔다.
        return "yufesta:ratelimit:cheer:ip:" + sha256(clientIp);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
