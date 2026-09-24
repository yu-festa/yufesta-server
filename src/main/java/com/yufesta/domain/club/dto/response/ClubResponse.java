package com.yufesta.domain.club.dto.response;

import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.timetable.entity.TimetableSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

/** 라인업 동아리 카드(FR-LU-01). 홈 캐러셀(FR-LU-02)과 전체 목록(FR-LU-03)도 같은 형태를 쓴다 */
@Builder
public record ClubResponse(
        Long id,
        @Schema(description = "동아리명", example = "HIPCOM") String name,
        @Schema(description = "한 줄 소개", example = "영남대학교 유일 힙합 동아리 HIPCOM") String intro,
        @Schema(description = "장르", example = "힙합") String genre,
        @Schema(description = "대표곡", example = "최준현-거북당") String signatureSong,
        @Schema(description = "인스타그램 링크", example = "https://www.instagram.com/hipcom_yu") String instagramUrl,
        @Schema(description = "대표 사진 URL. 없으면 null") String photoUrl,
        @Schema(description = "이 동아리의 공연들. 표시 순서대로") List<ClubPerformanceResponse> performances
) {

    public static ClubResponse of(Club club, List<TimetableSlot> performances) {
        return ClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .intro(club.getIntro())
                .genre(club.getGenre())
                .signatureSong(club.getSignatureSong())
                .instagramUrl(club.getInstagramUrl())
                .photoUrl(club.getPhotoUrl())
                .performances(performances.stream().map(ClubPerformanceResponse::from).toList())
                .build();
    }
}
