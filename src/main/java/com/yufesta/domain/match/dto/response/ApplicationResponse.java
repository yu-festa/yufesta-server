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
        @Schema(description = "보고 싶은 공연. 선택하지 않았으면 null") MatchSlotResponse wantedSlot,
        @Schema(description = "이월·재참여로 복사되며 원래 골랐던 공연이 이번 회차 규칙에 맞지 않아 비워졌으면 true. 재선택 안내용(FR-MT-35)", example = "false") boolean needsSlotReselect,
        @Schema(description = "신청 유형", example = "NEW") EntryType entryType,
        @Schema(description = "신청 시각") LocalDateTime createdAt
) {

    // 복사본(CARRIED·REJOIN)이고 원본엔 공연이 있는데 지금은 없으면 복사 때 규칙 위반으로 비워진 것이다
    private static boolean needsSlotReselect(Application application) {
        return application.getEntryType() != EntryType.NEW
                && application.getWantedSlot() == null
                && application.getSourceApplication() != null
                && application.getSourceApplication().getWantedSlot() != null;
    }

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
                .wantedSlot(application.getWantedSlot() == null ? null : MatchSlotResponse.from(application.getWantedSlot()))
                .needsSlotReselect(needsSlotReselect(application))
                .entryType(application.getEntryType())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
