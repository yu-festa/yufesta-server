package com.yufesta.domain.place.controller;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.place.dto.response.PlaceDetailResponse;
import com.yufesta.domain.place.dto.response.PlaceEventResponse;
import com.yufesta.domain.place.dto.response.PlaceListResponse;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.service.PlaceService;
import com.yufesta.support.ControllerTestSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = PlaceController.class)
class PlaceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private PlaceService placeService;

    @Test
    void 비로그인도_장소_목록을_조회한다() throws Exception {
        when(placeService.getPlaces(isNull())).thenReturn(List.of(placeListResponse()));

        mockMvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("중앙 무대"))
                .andExpect(jsonPath("$.data[0].category").value("STAGE"));
    }

    @Test
    void 카테고리로_장소를_필터링한다() throws Exception {
        when(placeService.getPlaces(PlaceCategory.TOILET))
                .thenReturn(List.of(placeListResponse(PlaceCategory.TOILET)));

        mockMvc.perform(get("/api/v1/places").param("category", "TOILET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].category").value("TOILET"));
    }

    @Test
    void 배달존_카테고리로_장소를_필터링한다() throws Exception {
        when(placeService.getPlaces(PlaceCategory.DELIVERY_ZONE))
                .thenReturn(List.of(placeListResponse(PlaceCategory.DELIVERY_ZONE)));

        mockMvc.perform(get("/api/v1/places").param("category", "DELIVERY_ZONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("DELIVERY_ZONE"));
    }

    @Test
    void 제거된_카테고리로는_장소를_필터링할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/places").param("category", "BOOTH"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"));
    }

    @Test
    void 비로그인도_장소_상세와_이벤트를_조회한다() throws Exception {
        when(placeService.getPlace(1L)).thenReturn(placeDetailResponse());

        mockMvc.perform(get("/api/v1/places/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].name").value("밴드 공연"))
                .andExpect(jsonPath("$.data.events[0].timeText").value("16:00 - 16:30"));
    }

    private static PlaceListResponse placeListResponse() {
        return placeListResponse(PlaceCategory.STAGE);
    }

    private static PlaceListResponse placeListResponse(PlaceCategory category) {
        return PlaceListResponse.builder()
                .id(1L)
                .name("중앙 무대")
                .category(category)
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .build();
    }

    private static PlaceDetailResponse placeDetailResponse() {
        PlaceEventResponse event = PlaceEventResponse.builder()
                .id(1L)
                .name("밴드 공연")
                .timeText("16:00 - 16:30")
                .sortOrder(1)
                .build();
        return PlaceDetailResponse.builder()
                .id(1L)
                .name("중앙 무대")
                .category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .events(List.of(event))
                .build();
    }
}
