package com.yufesta.common.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 무효화의 두 가지를 고정한다: 지우는 시점(커밋 뒤), 함께 지워야 하는 키(다른 응답에 실려 나가는 데이터)
 */
@ExtendWith(MockitoExtension.class)
class PublicCacheEvictorTest {

    private static final String TIMETABLE = "http:/api/v1/timetable";
    private static final String CLUBS = "http:/api/v1/clubs";

    @Mock
    private ResponseCache cache;

    @InjectMocks
    private PublicCacheEvictor evictor;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션_밖에서는_바로_지운다() {
        evictor.evictMatchSummary();

        verify(cache).evict(CacheKey.MATCH_SUMMARY);
    }

    @Test
    void 트랜잭션_안에서는_커밋된_뒤에_지운다() {
        TransactionSynchronizationManager.initSynchronization();

        evictor.evictMatchSummary();

        // 커밋 전에 지우면 그 틈에 들어온 요청이 옛 값을 읽어 캐시에 다시 넣는다
        verify(cache, never()).evict(any(String[].class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(cache).evict(CacheKey.MATCH_SUMMARY);
    }

    @Test
    void 롤백되면_지우지_않는다() {
        TransactionSynchronizationManager.initSynchronization();

        evictor.evictMatchSummary();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(cache, never()).evict(any(String[].class));
    }

    @Test
    void 공연이_바뀌면_라인업_목록과_그_동아리_상세도_지운다() {
        // 라인업 카드에 공연 시간·무대가 실려 있다. 동아리 없는 공연(null)은 건너뛴다
        evictor.evictTimetable(3L, null);

        verify(cache).evict(TIMETABLE, CLUBS, CLUBS + "/3");
    }

    @Test
    void 동아리가_없는_공연이_바뀌어도_라인업_목록은_지운다() {
        evictor.evictTimetable();

        verify(cache).evict(TIMETABLE, CLUBS);
    }
}
