package com.yufesta.common.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 저장소(운영). 자격 증명은 ECS 태스크 역할이 자동으로 준다(키 없음). 읽기는 CloudFront가 하므로 여기선 쓰기·삭제만
 */
public class S3ImageStorage implements ImageStorage {

    // 키에 uuid가 있어 내용이 바뀌지 않는다. 브라우저·CloudFront가 1년 캐시해도 안전
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3ImageStorage(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
        return properties.urlOf(key);
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .build());
    }
}
