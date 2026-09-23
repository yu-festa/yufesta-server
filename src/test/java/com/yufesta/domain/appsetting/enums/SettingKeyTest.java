package com.yufesta.domain.appsetting.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * enum의 키 목록과 초기 데이터 마이그레이션(V2__initial_data.sql)의 키가 서로 어긋나지 않도록 고정
 */
class SettingKeyTest {

    // 주석(--)이 아닌 값 행의 첫 문자열 리터럴: ('match.weight.tag', ...
    private static final Pattern SEED_ROW = Pattern.compile("^\\s*\\('([a-z_.]+)'", Pattern.MULTILINE);

    @Test
    void enum_키와_초기_데이터의_키가_같다() throws IOException {
        String seed = new ClassPathResource("db/migration/V2__initial_data.sql").getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = SEED_ROW.matcher(seed);
        Set<String> seedKeys = new java.util.HashSet<>();
        while (matcher.find()) {
            seedKeys.add(matcher.group(1));
        }

        Set<String> enumKeys = Arrays.stream(SettingKey.values())
                .map(SettingKey::key)
                .collect(Collectors.toSet());

        assertThat(enumKeys).hasSize(16).isEqualTo(seedKeys);
    }
}
