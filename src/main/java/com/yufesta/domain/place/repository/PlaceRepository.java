package com.yufesta.domain.place.repository;

import com.yufesta.domain.place.entity.Place;
import com.yufesta.domain.place.enums.PlaceCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    List<Place> findAllByCategoryAndActiveTrueOrderBySortOrderAscNameAsc(PlaceCategory category);

    Optional<Place> findByIdAndActiveTrue(Long id);
}
