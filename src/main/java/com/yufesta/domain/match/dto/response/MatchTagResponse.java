package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.enums.MatchTag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;

/**
 * 신청 화면의 관심 태그 선택지. 요청에는 code를 보낸다
 */
@Builder
public record MatchTagResponse(
        @Schema(description = "요청에 쓰는 코드", example = "MUSIC") MatchTag code,
        @Schema(description = "화면 표시 이름", example = "음악") String label
) {

    public static MatchTagResponse from(MatchTag tag) {
        return MatchTagResponse.builder()
                .code(tag)
                .label(tag.label())
                .build();
    }

    public static List<MatchTagResponse> all() {
        return Arrays.stream(MatchTag.values())
                .map(MatchTagResponse::from)
                .toList();
    }
}
