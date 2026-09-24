package com.yufesta.domain.match.dto.request;

import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Builder;

/**
 * 신청 내용 수정(FR-MT-14). 동의 항목은 바꿀 수 없어 포함하지 않는다
 */
@Builder
public record UpdateApplicationRequest(
        @Schema(description = "인스타그램 아이디", example = "yu.festa")
        @NotBlank @Size(max = 40) String instagramId,

        @Schema(description = "닉네임 2~8자", example = "수달")
        @NotBlank @Size(min = 2, max = 8) String nickname,

        @Schema(description = "성별", example = "F")
        @NotNull Gender gender,

        @Schema(description = "나이대(선택)", example = "22-24")
        AgeBand ageBand,

        @Schema(description = "관심 태그 코드, 최대 3개(선택)", example = "[\"MUSIC\"]")
        @Size(max = 3) Set<MatchTag> tags,

        @Schema(description = "한 줄 소개 40자 이내(선택)", example = "공연 같이 봐요")
        @Size(max = 40) String intro,

        @Schema(description = "보고 싶은 공연 슬롯 ID(선택). null이면 선택 해제", example = "4")
        Long wantedSlotId
) {
}
