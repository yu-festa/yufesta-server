package com.yufesta.domain.auth.dto.response;

import com.yufesta.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 현재 로그인 사용자의 상태. 식별 정보는 넣지 않는다(NFR-SC-07)
 */
@Builder
public record AuthMeResponse(
        @Schema(description = "역할", example = "USER") UserRole role
) {

    public static AuthMeResponse of(UserRole role) {
        return AuthMeResponse.builder()
                .role(role)
                .build();
    }
}
