package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Match;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    List<Match> findAllByRound_Id(Long roundId);

    // 이월 대상 계산: 방향성 2행이라 application_id만 모으면 매칭된 신청 전부가 나온다
    @Query("select distinct m.application.id from Match m where m.round.id = :roundId")
    Set<Long> findMatchedApplicationIdsByRoundId(@Param("roundId") Long roundId);

    // 배치 제외 쌍: 이전 회차에 이미 만난 회원 쌍(§8)
    @Query("""
            select new com.yufesta.domain.match.repository.UserIdPair(m.application.user.id, m.partnerApplication.user.id)
            from Match m
            where m.round.seq < :seq
            """)
    List<UserIdPair> findMatchedUserIdPairsBeforeSeq(@Param("seq") int seq);

    // 재배치(rerun) 전 정리. 벌크 삭제라 영속성 컨텍스트를 비운다
    @Modifying(clearAutomatically = true)
    @Query("delete from Match m where m.round.id = :roundId")
    int deleteAllByRoundId(@Param("roundId") Long roundId);
}
