package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Application;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 신청 조회. 취소 행(canceled_at)은 조회 조건에서 명시적으로 걸러야 한다
 */
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByUser_IdAndRound_Id(Long userId, Long roundId);

    // 이월 복사 전 선검사: 이미 다음 회차에 신청(취소 포함)이 있으면 uk_app_user_round에 걸리므로 건너뛴다
    boolean existsByUser_IdAndRound_Id(Long userId, Long roundId);

    long countByRound_IdAndCanceledAtIsNull(Long roundId);

    // 유니크 제약이 취소 행까지 포함하므로 선검사도 취소 여부를 보지 않는다
    boolean existsByRound_IdAndInstagramId(Long roundId, String instagramId);

    // 배치 풀: 해당 회차·미취소·매칭 차단 아님(§8). 태그까지 한 번에 읽어 N+1을 막는다
    @Query("""
            select distinct a from Application a
            join fetch a.user u
            left join fetch a.tags
            where a.round.id = :roundId
              and a.canceledAt is null
              and u.matchingBlockedAt is null
            """)
    List<Application> findPoolByRoundId(@Param("roundId") Long roundId);
}
