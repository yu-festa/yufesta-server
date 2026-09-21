package com.yufesta.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.place.dto.request.CreatePlaceRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceRequest;
import com.yufesta.domain.place.dto.response.AdminPlaceResponse;
import com.yufesta.domain.place.dto.response.PlaceDetailResponse;
import com.yufesta.domain.place.dto.response.PlaceListResponse;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.entity.PlaceEvent;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.repository.PlaceEventRepository;
import com.yufesta.domain.place.repository.PlaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceEventRepository placeEventRepository;

    @InjectMocks
    private PlaceService placeService;

    @Test
    void 카테고리_없이_노출_중인_장소를_표시순과_이름순으로_조회한다() {
        Place place = place(1L, "중앙 무대", PlaceCategory.STAGE, 1);
        when(placeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(place));

        List<PlaceListResponse> result = placeService.getPlaces(null);

        assertThat(result).extracting(PlaceListResponse::name).containsExactly("중앙 무대");
        verify(placeRepository).findAllByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Test
    void 카테고리로_노출_중인_장소를_표시순과_이름순으로_조회한다() {
        Place place = place(2L, "화장실", PlaceCategory.TOILET, 2);
        when(placeRepository.findAllByCategoryAndActiveTrueOrderBySortOrderAscNameAsc(PlaceCategory.TOILET))
                .thenReturn(List.of(place));

        List<PlaceListResponse> result = placeService.getPlaces(PlaceCategory.TOILET);

        assertThat(result).extracting(PlaceListResponse::category).containsExactly(PlaceCategory.TOILET);
        verify(placeRepository).findAllByCategoryAndActiveTrueOrderBySortOrderAscNameAsc(PlaceCategory.TOILET);
    }

    @Test
    void 장소_상세는_표시_순서대로_진행_이벤트를_반환한다() {
        Place place = place(1L, "중앙 무대", PlaceCategory.STAGE, 1);
        PlaceEvent firstEvent = PlaceEvent.builder()
                .place(place).name("밴드 공연").timeText("16:00 - 16:30").sortOrder(1)
                .build();
        PlaceEvent secondEvent = PlaceEvent.builder()
                .place(place).name("댄스 공연").timeText("16:30 - 17:00").sortOrder(2)
                .build();
        ReflectionTestUtils.setField(firstEvent, "id", 10L);
        ReflectionTestUtils.setField(secondEvent, "id", 11L);

        when(placeRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(place));
        when(placeEventRepository.findAllByPlace_IdOrderBySortOrderAsc(1L))
                .thenReturn(List.of(firstEvent, secondEvent));

        PlaceDetailResponse result = placeService.getPlace(1L);

        assertThat(result.events()).extracting(event -> event.name())
                .containsExactly("밴드 공연", "댄스 공연");
    }

    @Test
    void 노출되지_않거나_없는_장소는_찾을_수_없다() {
        when(placeRepository.findByIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.getPlace(999L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
    }

    @Test
    void 운영자는_장소의_표시순서와_노출여부를_직접_입력해_등록한다() {
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place savedPlace = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedPlace, "id", 3L);
            return savedPlace;
        });

        AdminPlaceResponse result = placeService.createPlace(createPlaceRequest());

        assertThat(result)
                .extracting(AdminPlaceResponse::id, AdminPlaceResponse::name,
                        AdminPlaceResponse::sortOrder, AdminPlaceResponse::active)
                .containsExactly(3L, "중앙 무대", 1, true);
    }

    @Test
    void 비노출_장소도_운영자가_수정해_다시_노출할_수_있다() {
        Place place = place(1L, "중앙 무대", PlaceCategory.STAGE, 1);
        place.update(
                place.getName(), place.getCategory(), place.getLatitude(), place.getLongitude(),
                place.getDescription(), place.getBuilding(), place.getFloor(), place.getSortOrder(), false
        );
        when(placeRepository.findById(1L)).thenReturn(Optional.of(place));

        AdminPlaceResponse result = placeService.updatePlace(1L, updatePlaceRequest());

        assertThat(result)
                .extracting(AdminPlaceResponse::name, AdminPlaceResponse::sortOrder, AdminPlaceResponse::active)
                .containsExactly("중앙 무대 수정", 2, true);
    }

    @Test
    void 없는_장소는_수정할_수_없다() {
        when(placeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> placeService.updatePlace(999L, updatePlaceRequest()))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PLACE_NOT_FOUND));
    }


    private Place place(Long id, String name, PlaceCategory category, int sortOrder) {
        Place place = Place.builder()
                .name(name)
                .category(category)
                .latitude(new BigDecimal("37.1234567"))
                .longitude(new BigDecimal("127.1234567"))
                .description("장소 설명")
                .building("학생회관")
                .floor("1층")
                .sortOrder(sortOrder)
                .active(true)
                .build();
        ReflectionTestUtils.setField(place, "id", id);
        return place;
    }

    private CreatePlaceRequest createPlaceRequest() {
        return CreatePlaceRequest.builder()
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

    private UpdatePlaceRequest updatePlaceRequest() {
        return UpdatePlaceRequest.builder()
                .name("중앙 무대 수정")
                .category(PlaceCategory.STAGE)
                .latitude(new BigDecimal("35.8365210"))
                .longitude(new BigDecimal("128.7542100"))
                .description("수정된 장소 설명")
                .building("학생회관")
                .floor("1층")
                .sortOrder(2)
                .active(true)
                .build();
    }
}
