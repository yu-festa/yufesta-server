package com.yufesta.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KoreanContentPolicyTest {

    private final KoreanContentPolicy policy = new KoreanContentPolicy(new KoreanContentNormalizer());

    @ParameterizedTest
    @ValueSource(strings = {
            "\uC2DC\uBC1C",
            "\uC528\uBC1C",
            "\uC2DC @ \uBC1C",
            "\u3145\u3142",
            "\uAC1C \uC0C8 \uB07C"
    })
    void 명확한_한국어_욕설과_우회_표현을_차단한다(String content) {
        assertThat(policy.findViolation(content, List.of()))
                .contains(LocalModerationViolation.PROFANITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\uC870\uC120\uC778\uC740 \uBBF8\uAC1C\uD574",
            "\uC678\uAD6D\uC778\uC740 \uC5F4!\uB4F1!\uD574",
            "\uC7A5\uC560\uC778\uC740 \uBBFC\uD3D0\uB2E4",
            "\uC774\uC8FC\uBBFC\uC740 \uB0B4\uCAD3\uC544\uC57C \uD55C\uB2E4"
    })
    void 집단_명칭과_비하_서술어가_같이_있으면_차단한다(String content) {
        assertThat(policy.findViolation(content, List.of()))
                .contains(LocalModerationViolation.HATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\uB108\uB97C \uC8FD\uC774\uACA0\uB2E4",
            "\uAC00\uB9CC\uB450\uC9C0 \uC54A\uACA0\uB2E4",
            "\uC8FD \uC5EC \uBC84 \uB9B0 \uB2E4"
    })
    void 명확한_위협_표현을_차단한다(String content) {
        assertThat(policy.findViolation(content, List.of()))
                .contains(LocalModerationViolation.THREAT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\uC2DC\uBC1C\uC810\uC740 \uC815\uBB38 \uC55E\uC785\uB2C8\uB2E4",
            "\uC870\uC120\uC778\uC758 \uC5ED\uC0AC\uC5D0 \uAD00\uD55C \uC804\uC2DC\uC785\uB2C8\uB2E4",
            "\uC678\uAD6D\uC778 \uD559\uC0DD\uB3C4 \uCC38\uC5EC\uD560 \uC218 \uC788\uC5B4\uC694"
    })
    void 정상_단어와_역사_문맥은_허용한다(String content) {
        assertThat(policy.findViolation(content, List.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uC6B4\uC601\uAE08\uCE59\uC5B4", "\uC6B4 \uC601 \uAE08 \uCE59 \uC5B4"})
    void 운영_설정_금칙어도_정규화해_차단한다(String content) {
        assertThat(policy.findViolation(content, List.of("\uC6B4\uC601\uAE08\uCE59어")))
                .contains(LocalModerationViolation.BANNED_WORD);
    }
}
