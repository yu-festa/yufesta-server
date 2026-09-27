package com.yufesta.common.cache;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 공개 읽기 API의 <b>완성된 응답 본문</b>을 캐시한다. 적중하면 컨트롤러·서비스·Hibernate·Jackson을 전혀 타지 않으므로
 * 부하 테스트에서 천장이었던 앱 CPU를 직접 줄인다(load/README.md).
 *
 * <h2>등록 조건 — 새 경로를 추가할 때 반드시 확인할 것</h2>
 * <ol>
 *   <li><b>모든 사용자에게 같은 응답</b>이어야 한다. 로그인 여부·쿠키·권한에 따라 한 글자라도 달라지면 등록하면 안 된다.
 *       예: 응원 메시지 목록은 {@code isMine}이 익명 키에 따라 달라지므로 제외한다.
 *       홈 요약도 로그인 시 {@code my}가 붙으므로 여기가 아니라 서비스 계층에서 공통부만 캐시한다</li>
 *   <li><b>GET</b>이고 부작용이 없어야 한다</li>
 *   <li>응답이 <b>JSON</b>이고 Set-Cookie를 만들지 않아야 한다(아래에서 한 번 더 걸러낸다)</li>
 *   <li><b>시각이 들어간 응답은 TTL이 곧 오차</b>가 된다. 타임테이블 응답의 {@code serverNow}·{@code isLive}는
 *       최대 (신선 기간 + stale-window)만큼 낡을 수 있다. 그래서 타임테이블 TTL을 10초로 가장 짧게 두었다.
 *       프론트는 서버 시각을 매 응답에서 다시 읽지 말고 한 번 받아 시계 오차를 구한 뒤 로컬 시계로 세는 것이 정확하다</li>
 * </ol>
 *
 * <h2>키와 변형</h2>
 * 키는 {@code http:<경로>[:<질의>]}이고 질의는 {@link #CACHEABLE_PARAMS}만 남겨 정규화한다.
 * 모르는 파라미터까지 키에 넣으면 누구나 `?x=1`, `?x=2`로 무한히 새 키를 만들어 캐시를 오염시킬 수 있다(키 폭발).
 * 허용 목록만 남기면 변형 수가 유한하다(카테고리 3종 × size 1~50).
 *
 * <h2>핫 키</h2>
 * 타임테이블 하나가 읽기 트래픽의 20%를 받는다. 키 하나에 부하가 몰리는 것 자체는 단일 노드 Redis에서 문제가 아니다
 * (초당 수만 건을 처리한다). 문제가 되는 경우는 두 가지이며 지금은 해당하지 않는다.
 * 클러스터에서 한 샤드만 뜨거워지는 경우(우리는 단일 노드), 값이 커서 네트워크가 병목인 경우(우리 응답은 수 KB).
 * 측정에서 Redis 왕복이 응답 시간의 큰 몫을 차지하면 그때 인메모리 1초 캐시를 앞에 한 겹 더 두는 것이 다음 수단이다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 5) // 접근 로그 필터보다 뒤. 캐시 적중이어도 로그는 남아야 한다
public class CachedResponseFilter extends OncePerRequestFilter {

    /** 경로 접두사 → 신선 기간. 변경 빈도가 낮을수록 길게 준다 */
    private static final Map<String, Duration> CACHEABLE_PATHS = new LinkedHashMap<>();

    static {
        CACHEABLE_PATHS.put("/api/v1/timetable", Duration.ofSeconds(10)); // 당일 운영자가 시각·지연을 고친다
        CACHEABLE_PATHS.put("/api/v1/clubs", Duration.ofSeconds(60));     // 축제 전에 확정되고 거의 안 바뀐다
        CACHEABLE_PATHS.put("/api/v1/places", Duration.ofSeconds(60));
        CACHEABLE_PATHS.put("/api/v1/notices", Duration.ofSeconds(30));   // 긴급 공지가 30초 안에는 보여야 한다
    }

    private static final Set<String> CACHEABLE_PARAMS = Set.of("category", "size");
    private static final String KEY_PREFIX = "http:";

    private final ResponseCache cache;

    public CachedResponseFilter(ResponseCache cache) {
        this.cache = cache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod()) || ttlOf(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Duration ttl = ttlOf(request.getRequestURI());
        // 체인이 돌았는지 기록해 둔다. 적중이면 여기서 본문을 직접 쓰고, 미스면 체인이 이미 응답을 채웠다
        boolean[] chainExecuted = {false};

        try {
            String body = cache.get(cacheKey(request), ttl, () -> {
                chainExecuted[0] = true;
                return runChainAndCapture(request, response, filterChain);
            });
            if (!chainExecuted[0] && body != null) {
                writeFromCache(response, body);
            }
        } catch (ChainFailure failure) {
            failure.rethrow();
        }
    }

    /** 컨트롤러를 태우고 본문을 가로챈다. 캐시하면 안 되는 응답이면 null을 돌려준다 */
    private String runChainAndCapture(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
            byte[] body = wrapper.getContentAsByteArray();
            wrapper.copyBodyToResponse(); // 가로챈 본문을 실제 응답으로 흘려보낸다. 이걸 빼면 빈 응답이 나간다
            return isCacheable(wrapper) ? new String(body, StandardCharsets.UTF_8) : null;
        } catch (IOException | ServletException exception) {
            throw new ChainFailure(exception); // Supplier는 검사 예외를 던질 수 없어 감싸서 올린다
        }
    }

    /**
     * 캐시해도 되는 응답인지 마지막으로 확인한다.
     * 200이 아닌 것(오류·404)을 캐시하면 잠깐의 장애가 TTL 동안 굳는다.
     * Set-Cookie가 붙은 응답은 사용자별 상태를 담고 있으므로 남의 쿠키를 나눠 줄 위험이 있다
     */
    private static boolean isCacheable(ContentCachingResponseWrapper wrapper) {
        return wrapper.getStatus() == HttpServletResponse.SC_OK
                && wrapper.getHeader(HttpHeaders.SET_COOKIE) == null
                && wrapper.getContentType() != null
                && wrapper.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE);
    }

    private static void writeFromCache(HttpServletResponse response, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    // 경로 접두사로 TTL을 찾는다. /api/v1/notices/banner 처럼 하위 경로도 같은 TTL을 쓴다
    private static Duration ttlOf(String uri) {
        return CACHEABLE_PATHS.entrySet().stream()
                .filter(entry -> uri.equals(entry.getKey()) || uri.startsWith(entry.getKey() + "/"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String cacheKey(HttpServletRequest request) {
        String variant = normalizedQuery(request.getQueryString());
        return KEY_PREFIX + request.getRequestURI() + (variant.isEmpty() ? "" : ":" + variant);
    }

    /** 허용 목록에 있는 파라미터만 남기고 이름순으로 정렬한다. 순서가 달라도 같은 키가 되게 하려는 것 */
    private static String normalizedQuery(String queryString) {
        if (!StringUtils.hasText(queryString)) {
            return "";
        }
        return Arrays.stream(queryString.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair.length == 2 && CACHEABLE_PARAMS.contains(pair[0]))
                .sorted(java.util.Comparator.comparing(pair -> pair[0]))
                .map(pair -> pair[0] + "=" + pair[1])
                .collect(Collectors.joining("&"));
    }

    /** 컨트롤러가 던진 검사 예외를 캐시 람다 밖으로 옮기기 위한 통로 */
    private static final class ChainFailure extends RuntimeException {

        private ChainFailure(Exception cause) {
            super(cause);
        }

        private void rethrow() throws ServletException, IOException {
            Throwable cause = getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw (ServletException) cause;
        }
    }

    /** 무효화 대상 키 목록. 운영자 쓰기 직후 이 키들을 지운다 */
    public static List<String> keysOf(String basePath) {
        return List.of(KEY_PREFIX + basePath);
    }
}
