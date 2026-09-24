package com.yufesta.common.config;

import com.yufesta.common.storage.ImageStorage;
import com.yufesta.common.storage.LocalImageStorage;
import com.yufesta.common.storage.S3ImageStorage;
import com.yufesta.common.storage.StorageProperties;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * app.storage.type에 따라 이미지 저장소 구현을 하나만 등록한다. local이면 저장 디렉터리를 /uploads/**로 서빙한다
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Client s3Client(StorageProperties properties) {
        // 자격 증명은 기본 체인(ECS 태스크 역할 → 환경변수 → 프로필). 코드에 키를 두지 않는다
        return S3Client.builder()
                .region(Region.of(properties.s3().region()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public ImageStorage s3ImageStorage(S3Client s3Client, StorageProperties properties) {
        return new S3ImageStorage(s3Client, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public ImageStorage localImageStorage(StorageProperties properties) {
        return new LocalImageStorage(properties);
    }

    // 로컬 저장 파일을 http://localhost:8080/uploads/<key>로 연다. SecurityConfig가 GET /uploads/**를 permitAll
    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public WebMvcConfigurer uploadsResourceHandler(StorageProperties properties) {
        String location = Path.of(properties.local().dir()).toAbsolutePath().normalize().toUri().toString();
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/uploads/**").addResourceLocations(location);
            }
        };
    }
}
