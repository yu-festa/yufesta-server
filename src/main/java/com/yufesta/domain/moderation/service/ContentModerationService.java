package com.yufesta.domain.moderation.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 저장 전 콘텐츠를 정규식·금칙어·OpenAI 모더레이션 순서로 검사한다. */
@Service
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);

    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?<!\\d)(?:\\+?82[-\\s]?)?01[016789][-\\s]?\\d{3,4}[-\\s]?\\d{4}(?!\\d)"
    );
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://|www\\.)[^\\s]+"
    );
    private static final Pattern SOCIAL_HANDLE = Pattern.compile(
            "(?<![A-Za-z0-9._])@[A-Za-z0-9][A-Za-z0-9._]{0,29}"
    );
    private static final Pattern CONTACT_PROMPT = Pattern.compile(
            "(?i)(?:카카오\\s*톡|카톡|디엠|dm|인스타(?:그램)?\\s*(?:아이디|id|디엠|dm)|"
                    + "연락처|전화번호|문자\\s*(?:주세요|줘|바람)|연락\\s*(?:주세요|줘|바람))"
    );

    private final AppSettingReader appSettingReader;
    private final OpenAiModerationClient openAiModerationClient;

    public ContentModerationService(
            AppSettingReader appSettingReader,
            OpenAiModerationClient openAiModerationClient
    ) {
        this.appSettingReader = appSettingReader;
        this.openAiModerationClient = openAiModerationClient;
    }

    /** 외부 호출 없이 개인 연락 수단과 운영 금칙어를 먼저 차단한다. */
    public void validateLocal(String content) {
        if (PHONE_NUMBER.matcher(content).find()
                || EMAIL.matcher(content).find()
                || URL.matcher(content).find()
                || SOCIAL_HANDLE.matcher(content).find()
                || CONTACT_PROMPT.matcher(content).find()) {
            reject("LOCAL_PATTERN", content);
        }

        String normalized = content.toLowerCase(Locale.ROOT);
        boolean containsBannedWord = appSettingReader.getList(SettingKey.FILTER_BANNED_WORDS).stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
        if (containsBannedWord) {
            reject("BANNED_WORD", content);
        }
    }

    /**
     * OpenAI 모더레이션 점수를 검사한다. 외부 API를 사용하지 않거나 호출이 실패하면 작성은 허용하되
     * 운영자가 나중에 확인할 수 있도록 SKIPPED 상태를 반환한다.
     */
    public ModerationStatus moderateByLlm(String content) {
        if (!appSettingReader.getBoolean(SettingKey.FILTER_LLM_ENABLED)) {
            return ModerationStatus.PASSED;
        }

        BigDecimal threshold = appSettingReader.getDecimal(SettingKey.FILTER_LLM_THRESHOLD);
        if (threshold.compareTo(BigDecimal.ZERO) < 0 || threshold.compareTo(BigDecimal.ONE) > 0) {
            throw new CustomException(ErrorCode.APP_SETTING_INVALID);
        }

        try {
            if (openAiModerationClient.exceedsThreshold(content, threshold)) {
                reject("LLM_THRESHOLD", content);
            }
            return ModerationStatus.PASSED;
        } catch (OpenAiModerationException exception) {
            // 본문 대신 해시만 남긴다. 실패 시 정상 사용자의 작성까지 막지 않는 FR-CF-03의 fail-open 정책이다.
            log.warn("콘텐츠 모더레이션을 건너뜁니다: reason={}, contentHash={}", exception.getMessage(), hash(content));
            return ModerationStatus.SKIPPED;
        }
    }

    private void reject(String reason, String content) {
        // 차단 이유·원문은 사용자에게 반환하지 않고, 운영 로그에는 분류와 해시만 남긴다.
        log.info("콘텐츠를 차단했습니다: reason={}, contentHash={}", reason, hash(content));
        throw new CustomException(ErrorCode.CONTENT_NOT_ALLOWED);
    }

    private static String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
