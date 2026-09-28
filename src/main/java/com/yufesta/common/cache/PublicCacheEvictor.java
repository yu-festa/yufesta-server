package com.yufesta.common.cache;

import com.yufesta.domain.place.enums.PlaceCategory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 운영자가 공개 콘텐츠를 고친 직후 캐시된 응답을 버린다. 무효화 키를 여기 한 곳에 모아 두는 이유는
 * 키 이름을 서비스마다 문자열로 흩어 놓으면 경로가 바뀔 때 조용히 어긋나기 때문이다.
 *
 * <h2>지우는 시점은 커밋 뒤다</h2>
 * 서비스는 트랜잭션 안에서 이 클래스를 부른다. 그 자리에서 바로 지우면 순서가 이렇게 된다.
 * <pre>
 *   운영자 트랜잭션: 수정 → 캐시 삭제 → ............ → 커밋
 *   다른 요청      :              캐시 없음 → DB 조회(아직 옛 값) → 캐시에 옛 값 저장
 * </pre>
 * 커밋 전이라 다른 요청에는 옛 값이 보이고, 그 옛 값이 새 신선 기간을 달고 캐시에 다시 들어간다.
 * 요청이 많을수록 이 틈에 걸릴 확률이 높다(발표 순간이 그렇다). 그래서 트랜잭션이 열려 있으면 커밋 뒤로 미룬다.
 * 롤백되면 지우지 않는다. 바뀐 것이 없기 때문이다.
 *
 * <h2>한계</h2>
 * 질의 파라미터가 붙은 변형(`?size=30` 같은)은 지우지 않는다. 변형마다 키가 따로라
 * 전부 열거하려면 파라미터 조합을 다 알아야 하는데, 값 범위가 넓으면(size 1~50) 실익이 없다.
 * 대신 TTL이 10~60초로 짧아 그만큼만 옛 값이 보인다. 기본 목록(파라미터 없음)·배너·상세는 즉시 지운다.
 */
@Component
public class PublicCacheEvictor {

    private static final String TIMETABLE = "http:/api/v1/timetable";
    private static final String CLUBS = "http:/api/v1/clubs";
    private static final String PLACES = "http:/api/v1/places";
    private static final String NOTICES = "http:/api/v1/notices";

    private final ResponseCache cache;

    public PublicCacheEvictor(ResponseCache cache) {
        this.cache = cache;
    }

    /**
     * 공연 등록·수정·삭제·시각 변경·지연·LIVE 지정·순서 변경 뒤.
     * 라인업 카드에는 공연 시간·무대가 실려 있어(타임테이블에서 가져온다) 라인업 목록도 함께 버린다.
     * @param clubIds 바뀐 공연에 연결된 동아리. 그 동아리의 상세 응답까지 지우려고 받는다. 없으면 비우고, null은 건너뛴다
     */
    public void evictTimetable(Long... clubIds) {
        List<String> keys = new ArrayList<>(List.of(TIMETABLE, CLUBS));
        for (Long clubId : clubIds) {
            if (clubId != null) {
                keys.add(CLUBS + "/" + clubId);
            }
        }
        evictAfterCommit(keys);
    }

    /** 동아리 등록·수정·삭제·사진 변경 뒤. 타임테이블 응답에도 동아리명이 들어가므로 함께 버린다 */
    public void evictClubs(Long clubId) {
        List<String> keys = keys(CLUBS, clubId);
        keys.add(TIMETABLE);
        evictAfterCommit(keys);
    }

    /** 장소 등록·수정 뒤. 카테고리 변형은 값이 3개뿐이라 전부 열거한다. 무대 이름은 타임테이블과 라인업 카드에도 나온다 */
    public void evictPlaces(Long placeId) {
        List<String> keys = keys(PLACES, placeId);
        for (PlaceCategory category : PlaceCategory.values()) {
            keys.add(PLACES + ":category=" + category.name());
        }
        keys.add(TIMETABLE);
        keys.add(CLUBS);
        evictAfterCommit(keys);
    }

    /**
     * 회차 상태·시각이 바뀐 직후(수동 오픈, 시각 수정, 마감, 발표). 홈 요약은 서비스 계층에서
     * 공통부만 캐시하므로 키도 URL이 아니라 도메인 이름이다.
     * <p>신청·취소로 바뀌는 신청자 수는 일부러 지우지 않는다. 마감 직전 초당 수십 건이 들어오는데
     * 그때마다 버리면 캐시가 없는 것과 같아진다. 2초 TTL만큼 늦게 보이는 것으로 충분하다.
     */
    public void evictMatchSummary() {
        evictAfterCommit(List.of(CacheKey.MATCH_SUMMARY));
    }

    /** 공지 등록·수정·삭제 뒤. 긴급 배너는 즉시 반영돼야 한다 */
    public void evictNotices(Long noticeId) {
        List<String> keys = keys(NOTICES, noticeId);
        keys.add(NOTICES + "/banner");
        evictAfterCommit(keys);
    }

    // 트랜잭션 안에서 불렸으면 커밋 뒤에, 밖에서 불렸으면 지금 지운다(클래스 주석 참고)
    private void evictAfterCommit(List<String> keys) {
        String[] targets = keys.toArray(String[]::new);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.evict(targets);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cache.evict(targets);
            }
        });
    }

    private static List<String> keys(String basePath, Long id) {
        List<String> keys = new ArrayList<>();
        keys.add(basePath);
        if (id != null) {
            keys.add(basePath + "/" + id);
        }
        return keys;
    }
}
