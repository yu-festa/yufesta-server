package com.yufesta.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KoreanContentNormalizerTest {

    private final KoreanContentNormalizer normalizer = new KoreanContentNormalizer();

    @Test
    void 공백_특수문자_zero_width와_반복문자를_정규화한다() {
        String input = "\uC528\uC774\uC774\uC774@\uBC1C\u200B";

        String result = normalizer.normalize(input);

        assertThat(result).isEqualTo("\uC528\uC774\uBC1C");
    }
}
