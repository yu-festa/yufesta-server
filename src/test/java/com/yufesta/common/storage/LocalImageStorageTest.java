package com.yufesta.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {

    @TempDir
    Path dir;

    @Test
    void 키_경로에_저장하고_cdn_url로_주소를_만들며_삭제는_멱등하다() throws Exception {
        StorageProperties properties = properties(dir);
        LocalImageStorage storage = new LocalImageStorage(properties);

        String url = storage.store("clubs/3/abc-1600.jpg", new byte[] {1, 2, 3}, "image/jpeg");

        assertThat(url).isEqualTo("http://localhost:8080/uploads/clubs/3/abc-1600.jpg");
        assertThat(Files.readAllBytes(dir.resolve("clubs/3/abc-1600.jpg"))).containsExactly(1, 2, 3);
        assertThat(properties.keyOf(url)).contains("clubs/3/abc-1600.jpg");
        assertThat(properties.keyOf("https://example.com/other.jpg")).isEmpty();

        storage.delete("clubs/3/abc-1600.jpg");
        storage.delete("clubs/3/abc-1600.jpg");

        assertThat(Files.exists(dir.resolve("clubs/3/abc-1600.jpg"))).isFalse();
    }

    @Test
    void 저장_디렉터리_밖으로_나가는_키는_거부한다() {
        LocalImageStorage storage = new LocalImageStorage(properties(dir));

        assertThatThrownBy(() -> storage.store("../escape.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StorageProperties properties(Path dir) {
        return new StorageProperties(
                "local",
                "http://localhost:8080/uploads",
                new StorageProperties.S3("", "ap-northeast-2"),
                new StorageProperties.Local(dir.toString())
        );
    }
}
