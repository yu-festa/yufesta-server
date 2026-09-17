package com.yufesta.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인가 규칙 테스트 전용 엔드포인트. @TestComponent라 컴포넌트 스캔에서 제외되며 @Import로만 사용한다
 */
@RestController
@TestComponent
public class SecurityRulesTestController {

    @GetMapping("/api/v1/ping")
    public String publicRead() {
        return "ok";
    }

    @PostMapping("/api/v1/ping")
    public String authenticatedWrite() {
        return "ok";
    }

    @PostMapping("/api/v1/cheers")
    public String anonymousCheerWrite() {
        return "ok";
    }

    @DeleteMapping("/api/v1/cheers/{id}")
    public String anonymousCheerDelete() {
        return "ok";
    }

    @GetMapping("/api/v1/admin/ping")
    public String staffRead() {
        return "ok";
    }

    @PostMapping("/api/v1/admin/match/rounds/{id}/publish")
    public String ownerPublish() {
        return "ok";
    }

    @GetMapping("/api/v1/admin/settings/ping")
    public String ownerSettings() {
        return "ok";
    }
}
