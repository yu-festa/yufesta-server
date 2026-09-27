package com.yufesta.common.cache;

/**
 * 캐시 키 이름 규칙. {@code yufesta:<버전>:<도메인>:<변형>} 형태로만 만든다.
 * <ul>
 *   <li><b>접두사 yufesta</b> — 한 Redis를 다른 용도(속도 제한, SSE)와 나눠 쓸 때 섞이지 않게 한다</li>
 *   <li><b>버전</b> — 응답 형태가 바뀌는 배포에서 값을 올리면 옛 캐시를 한 번에 버린다. 키를 지우러 다닐 필요가 없다</li>
 *   <li><b>도메인:변형</b> — 무효화 단위와 같게 맞춘다. 운영자가 타임테이블을 고치면 timetable 키만 지운다</li>
 * </ul>
 * 사용자별로 달라지는 응답은 키에 넣지 않는다. 회원 ID를 키에 넣으면 사용자 수만큼 키가 생겨
 * 적중률이 0에 가까워지고 메모리만 먹는다. 개인화 부분은 캐시하지 않고 요청마다 계산한다(예: 홈 요약의 my)
 */
public final class CacheKey {

    public static final String TIMETABLE = "timetable";
    public static final String CLUBS = "clubs";
    public static final String PLACES = "places";
    public static final String NOTICES = "notices";
    public static final String CHEERS = "cheers";
    public static final String MATCH_SUMMARY = "match:summary";

    private CacheKey() {
    }

    /** 변형이 없는 목록 키 */
    public static String of(String domain) {
        return domain;
    }

    /** 파라미터가 응답을 가르는 경우(카테고리·페이지 크기 등). 변형 수가 적을 때만 쓴다 */
    public static String of(String domain, Object variant) {
        return domain + ":" + (variant == null ? "all" : variant);
    }
}
