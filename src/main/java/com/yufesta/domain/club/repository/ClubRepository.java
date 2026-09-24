package com.yufesta.domain.club.repository;

import com.yufesta.domain.club.entity.Club;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, Long> {

    List<Club> findAllByOrderBySortOrderAscNameAsc();
}
