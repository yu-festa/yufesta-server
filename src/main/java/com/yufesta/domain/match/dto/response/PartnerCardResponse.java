package com.yufesta.domain.match.dto.response;

import com.yufesta.domain.match.entity.Application;
import com.yufesta.domain.match.entity.Match;
import com.yufesta.domain.match.enums.AgeBand;
import com.yufesta.domain.match.enums.MatchTag;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.Builder;

/**
 * 매칭 상대 카드(FR-MT-32). 상대의 회원 식별 정보와 점수는 넣지 않는다
 */
@Builder
public record PartnerCardResponse(
        @Schema(description = "매칭 ID. 신고 시 사용", example = "77") Long matchId,
        @Schema(description = "상대 닉네임", example = "수달") String nickname,
        @Schema(description = "상대 나이대", example = "22-24") AgeBand ageBand,
        @Schema(description = "상대 관심 태그") List<MatchTag> tags,
        @Schema(description = "나와 겹치는 태그(강조 표시용)") List<MatchTag> commonTags,
        @Schema(description = "상대가 보고 싶은 공연. 없으면 null") MatchSlotResponse wantedSlot,
        @Schema(description = "나와 같은 공연을 골랐으면 true(강조 표시용)", example = "true") boolean sameSlot,
        @Schema(description = "한 줄 소개") String intro,
        @Schema(description = "상대 인스타그램 아이디(복사·열기용)", example = "yu.festa") String instagramId
) {

    public static PartnerCardResponse of(Match match, Application mine) {
        Application partner = match.getPartnerApplication();
        Set<MatchTag> myTags = mine.getTags();
        TimetableSlot partnerSlot = partner.getWantedSlot();
        return PartnerCardResponse.builder()
                .matchId(match.getId())
                .nickname(partner.getNickname())
                .ageBand(partner.getAgeBand())
                .tags(sorted(partner.getTags()))
                .commonTags(sorted(partner.getTags().stream().filter(myTags::contains).toList()))
                .wantedSlot(partnerSlot == null ? null : MatchSlotResponse.from(partnerSlot))
                .sameSlot(partnerSlot != null && mine.getWantedSlot() != null && partnerSlot.getId().equals(mine.getWantedSlot().getId()))
                .intro(partner.getIntro())
                .instagramId(partner.getInstagramId())
                .build();
    }

    private static List<MatchTag> sorted(java.util.Collection<MatchTag> tags) {
        return tags.stream().sorted(Comparator.comparing(MatchTag::ordinal)).toList();
    }
}
