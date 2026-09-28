package com.yufesta.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MigrationVersionHistoryTest {

    @Test
    void 과거에_배포된_V8을_보존하고_소셜_프로필은_V9를_사용한다()
            throws IOException, NoSuchAlgorithmException {
        assertThat(migrationExists("V8__open_notification_subscriptions.sql"))
                .as("운영 DB에 적용된 V8 파일은 삭제하거나 다른 내용으로 교체하면 안 된다")
                .isTrue();
        assertThat(migrationSha256("V8__open_notification_subscriptions.sql"))
                .as("운영 DB에 기록된 V8 체크섬이 바뀌면 안 된다")
                .isEqualTo("55f2f53cd7b1ccabc9cf8febb0fed731becffba06186d812e0c5df8fc7600091");
        assertThat(migrationExists("V8__user_social_profile.sql"))
                .as("새 스키마 변경이 이미 사용한 V8 번호를 재사용하면 안 된다")
                .isFalse();
        assertThat(migrationExists("V9__user_social_profile.sql"))
                .as("소셜 프로필 변경은 다음 Flyway 버전인 V9를 사용해야 한다")
                .isTrue();
    }

    private boolean migrationExists(String filename) {
        return new ClassPathResource("db/migration/" + filename).exists();
    }

    private String migrationSha256(String filename) throws IOException, NoSuchAlgorithmException {
        byte[] content = new ClassPathResource("db/migration/" + filename).getContentAsByteArray();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
