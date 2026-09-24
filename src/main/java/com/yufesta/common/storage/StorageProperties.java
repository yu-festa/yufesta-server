package com.yufesta.common.storage;

import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이미지 저장소 설정(app.storage). type이 local이면 ./uploads + /uploads/**, s3면 버킷 + CloudFront.
 * 응답 URL은 항상 cdnUrl + "/" + 키 형태라 URL에서 키를 되찾을 수 있다(교체·삭제 때 씀)
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String cdnUrl,
        S3 s3,
        Local local
) {

    public record S3(String bucket, String region) {
    }

    public record Local(String dir) {
    }

    public String urlOf(String key) {
        return cdnUrl + "/" + key;
    }

    /** 우리 저장소 URL이면 키를, 아니면(운영자가 직접 넣은 외부 URL 등) 빈 값을 준다 */
    public Optional<String> keyOf(String url) {
        String prefix = cdnUrl + "/";
        if (url == null || !url.startsWith(prefix) || url.length() == prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(url.substring(prefix.length()));
    }
}
