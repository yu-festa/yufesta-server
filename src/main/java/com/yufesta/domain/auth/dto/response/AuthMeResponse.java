package com.yufesta.domain.auth.dto.response;

import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 현재 로그인 사용자의 상태와 본인에게만 노출하는 소셜 프로필.
 */
@Builder
public record AuthMeResponse(
        @Schema(description = "역할", example = "USER") UserRole role,
        @Schema(description = "소셜 로그인 표시 이름", example = "홍길동") String displayName,
        @Schema(description = "소셜 로그인 프로필 이미지 URL") String profileImageUrl
) {

    public static AuthMeResponse from(User user) {
        return AuthMeResponse.builder()
                .role(user.getRole())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }
}
