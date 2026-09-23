package com.yufesta.domain.appsetting.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.entity.AppSetting;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.repository.AppSettingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * app_settings 값을 타입별로 읽는다. 전체 행을 한 번에 읽어 30초 동안 메모리에 두고,
 * 지나면 다음 호출에서 다시 읽는다. 운영자가 값을 바꾼 직후에는 invalidate()로 즉시 반영한다
 */
@Service
@Transactional(readOnly = true)
public class AppSettingReader {

    private static final Logger log = LoggerFactory.getLogger(AppSettingReader.class);

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final String LIST_SEPARATOR = ",";

    private final AppSettingRepository appSettingRepository;
    private final Clock clock;

    // 여러 스레드가 동시에 읽어도 안전하도록 불변 스냅샷을 통째로 교체한다
    private volatile Snapshot snapshot;

    public AppSettingReader(AppSettingRepository appSettingRepository, Clock clock) {
        this.appSettingRepository = appSettingRepository;
        this.clock = clock;
    }

    /**
     * 정수 설정값을 읽는다.
     * @throws CustomException APP_SETTING_NOT_FOUND, APP_SETTING_INVALID
     */
    public int getInt(SettingKey key) {
        return parse(key, Integer::parseInt);
    }

    /**
     * 소수 설정값(가중치, 임계값)을 읽는다.
     * @throws CustomException APP_SETTING_NOT_FOUND, APP_SETTING_INVALID
     */
    public BigDecimal getDecimal(SettingKey key) {
        return parse(key, BigDecimal::new);
    }

    /**
     * 불리언 설정값을 읽는다. "1"·"true"는 참, "0"·"false"는 거짓.
     * @throws CustomException APP_SETTING_NOT_FOUND, APP_SETTING_INVALID
     */
    public boolean getBoolean(SettingKey key) {
        return parse(key, AppSettingReader::parseBoolean);
    }

    /**
     * 문자열 설정값을 그대로 읽는다.
     * @throws CustomException APP_SETTING_NOT_FOUND
     */
    public String getString(SettingKey key) {
        return value(key);
    }

    /**
     * 쉼표로 구분된 목록 설정값을 읽는다. 항목은 trim하고 빈 항목은 버린다(빈 문자열이면 빈 목록).
     * @throws CustomException APP_SETTING_NOT_FOUND
     */
    public List<String> getList(SettingKey key) {
        return Arrays.stream(value(key).split(LIST_SEPARATOR))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    /** 캐시를 비운다. 운영자 변경 API가 값을 바꾼 직후 호출한다. */
    public void invalidate() {
        snapshot = null;
    }

    private <T> T parse(SettingKey key, Function<String, T> parser) {
        String raw = value(key);
        try {
            return parser.apply(raw.trim());
        } catch (IllegalArgumentException exception) {
            log.error("app_settings 값 형식 오류: key={}, type={}", key.key(), key.type());
            throw new CustomException(ErrorCode.APP_SETTING_INVALID);
        }
    }

    private static boolean parseBoolean(String raw) {
        return switch (raw.toLowerCase()) {
            case "1", "true" -> true;
            case "0", "false" -> false;
            default -> throw new IllegalArgumentException("boolean이 아닌 값");
        };
    }

    private String value(SettingKey key) {
        String raw = currentValues().get(key.key());
        if (raw == null) {
            log.error("app_settings에 키가 없음: {}", key.key());
            throw new CustomException(ErrorCode.APP_SETTING_NOT_FOUND);
        }
        return raw;
    }

    // 스냅샷이 없거나 30초가 지났으면 다시 읽는다. 동시에 두 번 읽혀도 같은 내용이라 잠그지 않는다
    private Map<String, String> currentValues() {
        Instant now = clock.instant();
        Snapshot current = snapshot;
        if (current == null || current.isExpired(now)) {
            current = load(now);
            snapshot = current;
        }
        return current.values();
    }

    private Snapshot load(Instant loadedAt) {
        Map<String, String> values = appSettingRepository.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(AppSetting::getSettingKey, AppSetting::getSettingValue));
        return new Snapshot(values, loadedAt);
    }

    private record Snapshot(Map<String, String> values, Instant loadedAt) {

        boolean isExpired(Instant now) {
            return !now.isBefore(loadedAt.plus(CACHE_TTL));
        }
    }
}
