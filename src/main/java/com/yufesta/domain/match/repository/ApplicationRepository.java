package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Application;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 홈 요약의 my: 회차별(현재·최근 발표) 유효 신청과 매칭 여부를 한 번에 읽는다.
    // 회차마다 신청을 따로 읽고 매칭 여부를 또 읽으면 폴링 한 건에 쿼리가 3~4개 나간다.
    // matches는 방향성 2행이라 내 신청 id로 한 행이라도 있으면 매칭된 것이다
    @Query("""
            select new com.yufesta.domain.match.repository.MyApplicationState(
                a.round.id,
                case when exists (select 1 from Match m where m.application = a) then true else false end
            )
            from Application a
            where a.user.id = :userId
              and a.round.id in :roundIds
              and a.canceledAt is null
            """)
    List<MyApplicationState> findMyStates(@Param("userId") Long userId, @Param("roundIds") Collection<Long> roundIds);

    // 배치 풀: 해당 회차·미취소·매칭 차단 아님(§8). 태그·보고 싶은 공연까지 한 번에 읽어 N+1을 막는다
    @Query("""
            select distinct a from Application a
            join fetch a.user u
            left join fetch a.tags
            left join fetch a.wantedSlot
            where a.round.id = :roundId
              and a.canceledAt is null
              and u.matchingBlockedAt is null
            """)
    List<Application> findPoolByRoundId(@Param("roundId") Long roundId);

    // 공연 삭제 시 그 공연을 고른 신청의 선택만 비운다. 벌크 갱신이라 영속성 컨텍스트를 비운다
    @Modifying(clearAutomatically = true)
    @Query("update Application a set a.wantedSlot = null where a.wantedSlot.id = :slotId")
    int detachWantedSlot(@Param("slotId") Long slotId);
}
