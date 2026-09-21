package com.yufesta.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.place.dto.request.CreatePlaceRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceRequest;
import com.yufesta.domain.place.dto.response.AdminPlaceResponse;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.service.PlaceService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminPlaceController.class)
class AdminPlaceControllerTest extends ControllerTestSupport {

    @MockitoBean
    private PlaceService placeService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_장소를_등록한다() throws Exception {
        when(placeService.createPlace(any(CreatePlaceRequest.class))).thenReturn(placeResponse());

        mockMvc.perform(post("/api/v1/admin/places")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("장소를 등록했습니다."))
                .andExpect(jsonPath("$.data.name").value("중앙 무대"))
                .andExpect(jsonPath("$.data.sortOrder").value(1))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @WithMockLoginUser(role = UserRole.OWNER)
    void OWNER는_장소를_수정한다() throws Exception {
        when(placeService.updatePlace(any(Long.class), any(UpdatePlaceRequest.class))).thenReturn(placeResponse());

        mockMvc.perform(patch("/api/v1/admin/places/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("장소를 수정했습니다."));
    }


    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void USER는_장소_운영_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/places")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placeRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static AdminPlaceResponse placeResponse() {
        return AdminPlaceResponse.builder()
                .id(1L)
                .name("중앙 무대")
                .category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210"))
                .longitude(new BigDecimal("128.7542100"))
                .description("축제 주요 공연이 진행되는 무대")
                .building(null)
                .floor(null)
                .sortOrder(1)
                .active(true)
                .build();
    }

    private static String placeRequestJson() {
        return """
                {
                  "name": "중앙 무대",
                  "category": "STAGE",
                  "latitude": 35.8365210,
                  "longitude": 128.7542100,
                  "description": "축제 주요 공연이 진행되는 무대",
                  "sortOrder": 1,
                  "active": true
                }
                """;
    }
}
