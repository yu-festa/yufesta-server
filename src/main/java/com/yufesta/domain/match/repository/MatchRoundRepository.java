package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.MatchRound;
import com.yufesta.domain.match.enums.RoundStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회차 조회. 상태 전이는 findByIdForUpdate로 행을 잠근 뒤 수행한다(§5 동시성)
 */
public interface MatchRoundRepository extends JpaRepository<MatchRound, Long> {

    Optional<MatchRound> findBySeq(int seq);

    List<MatchRound> findAllByOrderBySeqAsc();

    Optional<MatchRound> findFirstByStatusInOrderBySeqAsc(Collection<RoundStatus> statuses);

    // 가장 최근에 발표된 회차. 결과 도착 표시(FR-MT-54)의 기준
    Optional<MatchRound> findFirstByPublishedAtIsNotNullOrderBySeqDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MatchRound r where r.id = :id")
    Optional<MatchRound> findByIdForUpdate(@Param("id") Long id);
}
