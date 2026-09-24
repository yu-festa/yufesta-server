package com.yufesta.domain.match.dto.request;

import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Builder;

/**
 * 인스타팅 신청(FR-MT-10·11). 형식 검증만 어노테이션으로 하고 인스타 ID 정규화·회차 규칙은 서비스에서 본다
 */
@Builder
public record ApplyMatchRequest(
        @Schema(description = "인스타그램 아이디. @와 대소문자는 서버가 정리한다", example = "@Yu.Festa")
        @NotBlank @Size(max = 40) String instagramId,

        @Schema(description = "닉네임 2~8자. 중복 허용", example = "펭귄")
        @NotBlank @Size(min = 2, max = 8) String nickname,

        @Schema(description = "성별", example = "F")
        @NotNull Gender gender,

        @Schema(description = "나이대(선택)", example = "22-24")
        AgeBand ageBand,

        @Schema(description = "관심 태그 코드, 최대 3개(선택)", example = "[\"MUSIC\", \"CAFE\"]")
        @Size(max = 3) Set<MatchTag> tags,

        @Schema(description = "한 줄 소개 40자 이내(선택)", example = "같이 공연 보실 분")
        @Size(max = 40) String intro,

        @Schema(description = "보고 싶은 공연 슬롯 ID(선택). GET /match/slots 목록 중 하나. 회차 발표 이후 시작 공연만", example = "4")
        Long wantedSlotId,

        @Schema(description = "동의한 이용약관 버전", example = "2026-09-01")
        @NotBlank @Size(max = 20) String termsVersion,

        @Schema(description = "동의한 개인정보 방침 버전", example = "2026-09-01")
        @NotBlank @Size(max = 20) String privacyVersion,

        @Schema(description = "만 19세 이상 확인. true여야 한다", example = "true")
        @AssertTrue boolean ageConfirmed
) {
}
