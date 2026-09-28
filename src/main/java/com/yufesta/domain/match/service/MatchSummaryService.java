package com.yufesta.domain.match.service;

import com.yufesta.common.cache.CacheKey;
import com.yufesta.common.cache.ResponseCache;
import com.yufesta.domain.match.dto.response.MatchSummaryCommonResponse;
import com.yufesta.domain.match.dto.response.MatchSummaryResponse;
import com.yufesta.domain.match.dto.response.MySummaryResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 홈 인스타팅 블록 조회(FR-MT-50~56). 캐시와 DB 조회를 조립한다.
 *
 * <p>홈은 발표 시각을 기다리며 몇 초 간격으로 폴링하는 화면이라 축제 당일 요청 수가 가장 많다.
 * 부하 테스트에서 발표 직후 전체 33,273건 중 32,273건(97%)이 이 폴링이었다(load/README.md).
 * 그래서 모두에게 같은 부분만 {@link MatchSummaryCommonResponse}로 떼어 캐시하고,
 * 서버 시각과 내 상태는 요청마다 계산한다.
 *
 * <p><b>이 클래스에는 {@code @Transactional}을 붙이지 않는다</b>(다른 서비스와 다른 점).
 * 붙이면 캐시가 맞아 DB가 필요 없는 요청도 트랜잭션을 열어 커넥션을 빌린다.
 * DB를 읽는 부분은 {@link MatchSummaryQueryService}에 있고 트랜잭션 경계도 거기다.
 * 그래서 비로그인 요청은 캐시가 맞으면 DB와 한 번도 통신하지 않는다.
 */
@Service
public class MatchSummaryService {

    private static final Logger log = LoggerFactory.getLogger(MatchSummaryService.class);

    /**
     * 공통부 신선 기간. 신청자 수가 실시간처럼 보여야 해서 짧게 잡는다.
     * 이 값만으로 원본 계산이 초당 0.5회로 줄어(200 req/s 기준) 더 늘릴 이유가 없다
     */
    private static final Duration COMMON_TTL = Duration.ofSeconds(2);

    private final MatchSummaryQueryService summaryQueryService;
    private final ResponseCache responseCache;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MatchSummaryService(
            MatchSummaryQueryService summaryQueryService,
            ResponseCache responseCache,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.summaryQueryService = summaryQueryService;
        this.responseCache = responseCache;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 홈 블록 데이터를 한 번에 반환한다. 비로그인(userId null)이면 my는 null.
     * <p>회차·신청자 수는 최대 {@code COMMON_TTL}만큼 낡을 수 있다. 회차 상태·시각이 바뀌는 순간은
     * 운영자·스케줄러 경로에서 캐시를 버리므로(PublicCacheEvictor) 발표가 늦게 보이지 않는다.
     * <p>공통부와 내 상태를 서로 다른 트랜잭션에서 읽는다. 둘 사이에 회차가 바뀌면 한 응답 안에서
     * 잠깐 어긋날 수 있으나, 공통부가 이미 최대 2초 낡을 수 있는 값이라 새로 생기는 오차가 아니다.
     * @throws CustomException MATCH_ROUND_NOT_FOUND
     */
    public MatchSummaryResponse getSummary(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        MatchSummaryCommonResponse common = cachedCommon();
        MySummaryResponse my = userId == null ? null : summaryQueryService.loadMy(userId);
        return MatchSummaryResponse.of(now, common, my);
    }

    // 캐시가 비었거나 꺼져 있거나 Redis가 죽으면 loadCommon()만 도는 것과 같다(fail-open)
    private MatchSummaryCommonResponse cachedCommon() {
        String json = responseCache.get(
                CacheKey.MATCH_SUMMARY,
                COMMON_TTL,
                () -> objectMapper.writeValueAsString(summaryQueryService.loadCommon())
        );
        try {
            return objectMapper.readValue(json, MatchSummaryCommonResponse.class);
        } catch (RuntimeException exception) {
            // 응답 형태가 바뀐 배포 직후 옛 값이 남아 있는 경우. 키 버전이 보통 막지만 여기서도 DB로 되돌린다
            log.warn("홈 요약 캐시를 읽지 못해 DB로 계산한다: {}", exception.getMessage());
            return summaryQueryService.loadCommon();
        }
    }
}
