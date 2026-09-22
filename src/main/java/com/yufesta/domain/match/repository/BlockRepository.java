package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.enums.BlockDecision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 매칭 신고·차단 조회
 */
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByReporter_IdAndTarget_Id(Long reporterId, Long targetId);

    List<Block> findAllByReporter_Id(Long reporterId);

    // 배치 제외 쌍: 신고·차단은 검토 결과와 무관하게 재매칭을 막는다(FR-MT-41)
    @Query("select new com.yufesta.domain.match.repository.UserIdPair(b.reporter.id, b.target.id) from Block b")
    List<UserIdPair> findAllUserIdPairs();

    // 누적 신고 수: 기각(DISMISS)된 건은 제외. decision이 null인 미검토 건은 포함해야 하므로 <> 만으로는 안 된다
    @Query("""
            select count(b) from Block b
            where b.target.id = :targetId
              and (b.decision is null or b.decision <> :dismissed)
            """)
    long countValidReports(@Param("targetId") Long targetId, @Param("dismissed") BlockDecision dismissed);
}
