package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Match;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 매칭 결과 조회. 방향성 2행 저장이라 내 신청 id 하나로 상대 목록이 나온다
 */
public interface MatchRepository extends JpaRepository<Match, Long> {

    // 결과 카드용: 상대 신청과 태그까지 한 번에, 점수 내림차순(FR-MT-32)
    @Query("""
            select distinct m from Match m
            join fetch m.partnerApplication p
            left join fetch p.tags
            where m.application.id = :applicationId
            order by m.score desc, m.id asc
            """)
    List<Match> findAllByApplicationId(@Param("applicationId") Long applicationId);

    boolean existsByApplication_Id(Long applicationId);

    long countByRound_Id(Long roundId);
}
