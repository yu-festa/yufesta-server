package com.yufesta.domain.place.repository;

import com.yufesta.domain.place.entity.PlaceEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceEventRepository extends JpaRepository<PlaceEvent, Long> {

    List<PlaceEvent> findAllByPlace_IdOrderBySortOrderAsc(Long placeId);

    Optional<PlaceEvent> findByIdAndPlace_Id(Long id, Long placeId);
}
