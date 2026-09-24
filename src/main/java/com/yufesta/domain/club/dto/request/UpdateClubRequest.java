package com.yufesta.domain.club.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** 동아리 카드 수정 요청. 전체 필드를 다시 보낸다 */
@Builder
public record UpdateClubRequest(
        @Schema(description = "동아리명", example = "HIPCOM") @NotBlank @Size(max = 50) String name,
        @Schema(description = "한 줄 소개", example = "영남대학교 유일 힙합 동아리 HIPCOM") @NotBlank @Size(max = 200) String intro,
        @Schema(description = "장르", example = "힙합") @Size(max = 30) String genre,
        @Schema(description = "대표곡", example = "최준현-거북당") @Size(max = 50) String signatureSong,
        @Schema(description = "인스타그램 링크(https://instagram.com/ 또는 https://www.instagram.com/ 로 시작)", example = "https://www.instagram.com/hipcom_yu")
        @Size(max = 200) @Pattern(regexp = "^https://(www\\.)?instagram\\.com/.+$", message = "인스타그램 링크 형식이 아닙니다.")
        String instagramUrl,
        @Schema(description = "대표 사진 URL(선택). null이면 제거", example = "https://example.com/hipcom.jpg") @Size(max = 500) String photoUrl,
        @Schema(description = "표시 순서", example = "3") @NotNull Integer sortOrder
) {
}
