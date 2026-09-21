package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.EntryType;
import com.yufesta.domain.match.enums.Gender;
import com.yufesta.domain.match.enums.MatchTag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;

/**
 * 본인 신청 내역. 본인 것이라 인스타 ID를 포함하지만 회원 식별 정보는 넣지 않는다
 */
@Builder
public record ApplicationResponse(
        @Schema(description = "신청 ID", example = "12") Long id,
        @Schema(description = "회차 번호", example = "1") int roundSeq,
        @Schema(description = "정규화된 인스타그램 아이디", example = "yu.festa") String instagramId,
        @Schema(description = "닉네임", example = "펭귄") String nickname,
        @Schema(description = "성별", example = "F") Gender gender,
        @Schema(description = "나이대", example = "22-24") AgeBand ageBand,
        @Schema(description = "관심 태그") List<MatchTag> tags,
        @Schema(description = "한 줄 소개") String intro,
        @Schema(description = "신청 유형", example = "NEW") EntryType entryType,
        @Schema(description = "신청 시각") LocalDateTime createdAt
) {

    public static ApplicationResponse from(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .roundSeq(application.getRound().getSeq())
                .instagramId(application.getInstagramId())
                .nickname(application.getNickname())
                .gender(application.getGender())
                .ageBand(application.getAgeBand())
                .tags(application.getTags().stream().sorted(Comparator.comparing(MatchTag::ordinal)).toList())
                .intro(application.getIntro())
                .entryType(application.getEntryType())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
