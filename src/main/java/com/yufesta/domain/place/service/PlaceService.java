package com.yufesta.domain.place.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.place.dto.response.PlaceDetailResponse;
import com.yufesta.domain.place.dto.response.PlaceEventResponse;
import com.yufesta.domain.place.dto.response.PlaceListResponse;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.repository.PlaceEventRepository;
import com.yufesta.domain.place.repository.PlaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 장소 공개 조회를 처리
 */
@Service
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceEventRepository placeEventRepository;

    public PlaceService(PlaceRepository placeRepository, PlaceEventRepository placeEventRepository) {
        this.placeRepository = placeRepository;
        this.placeEventRepository = placeEventRepository;
    }

    /**
     * 노출 중인 장소 목록을 조회한다.
     * <p>표시 순서와 이름순으로 반환하며, 거리순 정렬은 프론트에서 처리한다(FR-MAP-01, 02, 06).
     */
    public List<PlaceListResponse> getPlaces(PlaceCategory category) {
        List<Place> places = getActivePlaces(category);

        return places.stream()
                .map(PlaceListResponse::from)
                .toList();
    }

    /**
     * 장소 상세와 진행 이벤트를 조회한다.
     * <p>진행 이벤트는 표시 순서대로 반환한다(FR-MAP-03).
     * @throws CustomException PLACE_NOT_FOUND
     */
    public PlaceDetailResponse getPlace(Long placeId) {
        Place place = placeRepository.findByIdAndActiveTrue(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLACE_NOT_FOUND));

        List<PlaceEventResponse> events = placeEventRepository.findAllByPlace_IdOrderBySortOrderAsc(placeId)
                .stream()
                .map(PlaceEventResponse::from)
                .toList();

        return PlaceDetailResponse.of(place, events);
    }

    private List<Place> getActivePlaces(PlaceCategory category) {
        if (category == null) {
            return placeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc();
        }
        return placeRepository.findAllByCategoryAndActiveTrueOrderBySortOrderAscNameAsc(category);
    }
}
