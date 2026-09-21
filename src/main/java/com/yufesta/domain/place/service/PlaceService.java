package com.yufesta.domain.place.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.place.dto.request.CreatePlaceEventRequest;
import com.yufesta.domain.place.dto.request.CreatePlaceRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceEventRequest;
import com.yufesta.domain.place.dto.request.UpdatePlaceRequest;
import com.yufesta.domain.place.dto.response.AdminPlaceResponse;
import com.yufesta.domain.place.dto.response.PlaceDetailResponse;
import com.yufesta.domain.place.dto.response.PlaceEventResponse;
import com.yufesta.domain.place.dto.response.PlaceListResponse;
import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.entity.PlaceEvent;
import com.yufesta.domain.place.enums.PlaceCategory;
import com.yufesta.domain.place.repository.PlaceEventRepository;
import com.yufesta.domain.place.repository.PlaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 장소 조회와 운영자 관리를 처리
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
     * 장소 상세와 연결된 이벤트를 조회한다.
     * <p>이벤트는 표시 순서대로 반환한다(FR-MAP-03).
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

    /**
     * 장소를 등록한다.
     * <p>운영자가 입력한 표시 순서와 노출 여부를 그대로 저장한다(FR-ADM-08).
     */
    @Transactional
    public AdminPlaceResponse createPlace(CreatePlaceRequest request) {
        Place place = Place.builder()
                .name(request.name())
                .category(request.category())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .description(request.description())
                .building(request.building())
                .floor(request.floor())
                .sortOrder(request.sortOrder())
                .active(request.active())
                .build();

        return AdminPlaceResponse.from(placeRepository.save(place));
    }

    /**
     * 장소 정보를 수정한다.
     * <p>비노출 장소도 운영자가 다시 노출할 수 있도록 조회 대상에 포함한다(FR-ADM-08).
     * @throws CustomException PLACE_NOT_FOUND
     */
    @Transactional
    public AdminPlaceResponse updatePlace(Long placeId, UpdatePlaceRequest request) {
        Place place = getPlaceOrThrow(placeId);
        place.update(
                request.name(),
                request.category(),
                request.latitude(),
                request.longitude(),
                request.description(),
                request.building(),
                request.floor(),
                request.sortOrder(),
                request.active()
        );

        return AdminPlaceResponse.from(place);
    }

    /**
     * 장소에 연결할 이벤트를 등록한다.
     * <p>이벤트는 현재 시간과 관계없이 장소 상세에 표시될 목록으로 저장한다(FR-ADM-08).
     * @throws CustomException PLACE_NOT_FOUND
     */
    @Transactional
    public PlaceEventResponse createPlaceEvent(Long placeId, CreatePlaceEventRequest request) {
        Place place = getPlaceOrThrow(placeId);
        PlaceEvent placeEvent = PlaceEvent.builder()
                .place(place)
                .name(request.name())
                .timeText(request.timeText())
                .sortOrder(request.sortOrder())
                .build();

        return PlaceEventResponse.from(placeEventRepository.save(placeEvent));
    }

    /**
     * 장소에 연결된 이벤트를 수정한다.
     * <p>URL의 장소와 연결되지 않은 이벤트는 수정할 수 없다(FR-ADM-08).
     * @throws CustomException PLACE_NOT_FOUND, PLACE_EVENT_NOT_FOUND
     */
    @Transactional
    public PlaceEventResponse updatePlaceEvent(Long placeId, Long placeEventId, UpdatePlaceEventRequest request) {
        getPlaceOrThrow(placeId);
        PlaceEvent placeEvent = placeEventRepository.findByIdAndPlace_Id(placeEventId, placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLACE_EVENT_NOT_FOUND));
        placeEvent.update(request.name(), request.timeText(), request.sortOrder());

        return PlaceEventResponse.from(placeEvent);
    }

    private List<Place> getActivePlaces(PlaceCategory category) {
        if (category == null) {
            return placeRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc();
        }
        return placeRepository.findAllByCategoryAndActiveTrueOrderBySortOrderAscNameAsc(category);
    }

    private Place getPlaceOrThrow(Long placeId) {
        return placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLACE_NOT_FOUND));
    }
}
