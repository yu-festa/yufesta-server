package com.yufesta.domain.moderation.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 공백과 유니코드를 이용한 필터 우회를 제거하여 비교용 문자열을 생성한다. */
@Component
public class KoreanContentNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}+");
    private static final Pattern SEPARATORS = Pattern.compile("[\\p{Z}\\p{P}\\p{S}]+");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1+");

    public String normalize(String content) {
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        normalized = INVISIBLE.matcher(normalized).replaceAll("");
        normalized = SEPARATORS.matcher(normalized).replaceAll("");
        return REPEATED.matcher(normalized).replaceAll("$1");
    }
}
