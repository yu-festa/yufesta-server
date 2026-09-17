package com.yufesta.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. 애플리케이션 클래스에 두면 @WebMvcTest 슬라이스까지 JPA를 요구하므로 분리
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
