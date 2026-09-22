package com.yufesta.domain.match.repository;

import com.yufesta.domain.match.entity.Block;
import com.yufesta.domain.match.enums.BlockDecision;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 매칭 신고·차단 조회
 */
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByReporter_IdAndTarget_Id(Long reporterId, Long targetId);

    List<Block> findAllByReporter_Id(Long reporterId);

    // 운영자가 CONFIRM한 신고가 하나라도 있으면 임계와 무관하게 제재(FR-MT-42 앞당김)
    boolean existsByTarget_IdAndDecision(Long targetId, BlockDecision decision);

    // 운영자 검토 목록(FR-ADM-03). 신고자·대상·회차를 함께 읽어 N+1을 막는다. 오프셋 페이지는 운영자 화면에만(§9)
    @EntityGraph(attributePaths = {"reporter", "target", "round"})
    List<Block> findAllByOrderByIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "target", "round"})
    List<Block> findAllByReviewedAtIsNullOrderByIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"reporter", "target", "round"})
    List<Block> findAllByReviewedAtIsNotNullOrderByIdDesc(Pageable pageable);

    // 목록 한 페이지의 대상별 유효 신고 수를 group by 한 번으로
    @Query("""
            select new com.yufesta.domain.match.repository.TargetReportCount(b.target.id, count(b))
            from Block b
            where b.target.id in :targetIds
              and (b.decision is null or b.decision <> :dismissed)
            group by b.target.id
            """)
    List<TargetReportCount> countValidReportsByTargetIds(
            @Param("targetIds") Collection<Long> targetIds,
            @Param("dismissed") BlockDecision dismissed
    );

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
