package com.yufesta.common.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 로컬 파일 저장소(dev·test). app.storage.local.dir 아래에 키 그대로 저장하고 StorageConfig가 /uploads/**로 서빙한다
 */
public class LocalImageStorage implements ImageStorage {

    private final Path root;
    private final StorageProperties properties;

    public LocalImageStorage(StorageProperties properties) {
        this.root = Path.of(properties.local().dir()).toAbsolutePath().normalize();
        this.properties = properties;
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("이미지 로컬 저장 실패: " + key, exception);
        }
        return properties.urlOf(key);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new UncheckedIOException("이미지 로컬 삭제 실패: " + key, exception);
        }
    }

    // 키에 ".."이 섞여 저장 디렉터리 밖으로 나가지 못하게 막는다
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 저장 키: " + key);
        }
        return target;
    }
}
