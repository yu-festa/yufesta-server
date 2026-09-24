package com.yufesta.domain.cheer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 응원 메시지 작성 요청 */
@Builder
public record CreateCheerRequest(
        @Schema(description = "응원 메시지", example = "축제 파이팅!")
        @NotBlank
        @Size(max = 40)
        String content
) {
}
