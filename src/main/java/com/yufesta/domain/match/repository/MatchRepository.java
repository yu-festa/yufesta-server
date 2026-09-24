package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Match;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 매칭 결과 조회. 방향성 2행 저장이라 내 신청 id 하나로 상대 목록이 나온다
 */
public interface MatchRepository extends JpaRepository<Match, Long> {

    // 결과 카드용: 상대 신청·회원·태그·보고 싶은 공연까지 한 번에, 점수 내림차순(FR-MT-32). 회원은 신고 제외 판정에 쓴다
    @Query("""
            select distinct m from Match m
            join fetch m.partnerApplication p
            join fetch p.user
            left join fetch p.tags
            left join fetch p.wantedSlot
            where m.application.id = :applicationId
            order by m.score desc, m.id asc
            """)
    List<Match> findAllByApplicationId(@Param("applicationId") Long applicationId);

    // 신고 접수용: 두 신청과 회차(발표 여부)를 한 번에 읽는다. 회원은 일부러 프록시로 둔다(id만 필요).
    // 대상 회원을 여기서 읽어 두면 뒤의 잠금 조회가 이미 있는 객체를 돌려주며 상태를 새로 읽지 않아,
    // 다른 트랜잭션이 방금 커밋한 차단 상태가 보이지 않는다. 대상은 UserService.getUserForUpdate가 처음 읽어야 한다
    @Query("""
            select m from Match m
            join fetch m.round
            join fetch m.application
            join fetch m.partnerApplication
            where m.id = :id
            """)
    Optional<Match> findByIdWithParticipants(@Param("id") Long id);

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
