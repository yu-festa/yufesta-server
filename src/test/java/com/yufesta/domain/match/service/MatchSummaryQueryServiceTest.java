package com.yufesta.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.match.dto.response.MatchSummaryCommonResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 홈 폴링 한 건마다 도는 조회가 DB를 몇 번 다녀오는지 고정한다.
 * 값이 맞는지는 MatchSummaryServiceTest·ApplicationRepositoryTest가 보고, 여기서는 비용만 본다
 */
@SpringBootTest
@ActiveProfiles("test")
class MatchSummaryQueryServiceTest {

    @Autowired
    private MatchSummaryQueryService summaryQueryService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    void 내_상태_조회는_쿼리_하나이고_트랜잭션을_열지_않는다() {
        MatchSummaryCommonResponse common = MatchSummaryCommonResponse.builder()
                .applicantCount(0)
                .currentRoundId(2L)
                .publishedRoundId(1L)
                .publishedRoundSeq(1)
                .build();

        summaryQueryService.loadMy(7L, common);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        // 트랜잭션을 열면 SELECT 하나를 위해 제어문 5개가 더 나간다(로컬 general log로 측정: 건당 6회 중 5회)
        assertThat(statistics.getTransactionCount()).isZero();
    }
}
