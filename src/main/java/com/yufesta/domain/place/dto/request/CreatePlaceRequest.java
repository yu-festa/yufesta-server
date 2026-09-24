package com.yufesta.domain.place.dto.request;

import com.yufesta.domain.place.enums.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Builder;

/** 장소 등록 요청 */
@Builder
public record CreatePlaceRequest(
        @Schema(description = "장소명", example = "중앙 무대")
        @NotBlank
        @Size(max = 50)
        String name,

        @Schema(description = "장소 카테고리(STAGE: 공연장 / TOILET: 화장실 / DELIVERY_ZONE: 배달존)", example = "STAGE")
        @NotNull
        PlaceCategory category,

        @Schema(description = "위도", example = "35.8365210")
        @NotNull
        @DecimalMin("-90.0000000")
        @DecimalMax("90.0000000")
        @Digits(integer = 2, fraction = 7)
        BigDecimal latitude,

        @Schema(description = "경도", example = "128.7542100")
        @NotNull @DecimalMin("-180.0000000") @DecimalMax("180.0000000")
        @Digits(integer = 3, fraction = 7)
        BigDecimal longitude,

        @Schema(description = "장소 설명", example = "축제 주요 공연이 진행되는 무대")
        @Size(max = 200)
        String description,

        @Schema(description = "건물명", example = "학생회관")
        @Size(max = 50)
        String building,

        @Schema(description = "층 또는 세부 위치", example = "1층 서편")
        @Size(max = 20)
        String floor,

        @Schema(description = "표시 순서", example = "1")
        @NotNull
        Integer sortOrder,

        @Schema(description = "노출 여부", example = "true")
        @NotNull
        Boolean active
) {
}
