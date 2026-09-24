package com.yufesta.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

    @Mock
    private S3Client s3Client;

    private final StorageProperties properties = new StorageProperties(
            "s3",
            "https://dxxxx.cloudfront.net",
            new StorageProperties.S3("yufesta-images", "ap-northeast-2"),
            new StorageProperties.Local("./uploads")
    );

    @Test
    void 버킷_키_contentType_캐시_헤더로_올리고_CloudFront_주소를_돌려준다() {
        S3ImageStorage storage = new S3ImageStorage(s3Client, properties);

        String url = storage.store("clubs/3/abc-1600.jpg", new byte[] {1, 2}, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("yufesta-images");
        assertThat(captor.getValue().key()).isEqualTo("clubs/3/abc-1600.jpg");
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(captor.getValue().cacheControl()).contains("immutable");
        assertThat(url).isEqualTo("https://dxxxx.cloudfront.net/clubs/3/abc-1600.jpg");
    }

    @Test
    void 삭제는_버킷과_키로_DeleteObject를_부른다() {
        new S3ImageStorage(s3Client, properties).delete("clubs/3/abc-thumb.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("yufesta-images");
        assertThat(captor.getValue().key()).isEqualTo("clubs/3/abc-thumb.jpg");
    }
}
