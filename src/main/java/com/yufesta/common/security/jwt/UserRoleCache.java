package com.yufesta.common.security.jwt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;

/**
 * 회원 id → 역할을 태스크 메모리에 잠깐 기억한다. 로그인한 요청마다 회원을 DB에서 읽지 않기 위해서다.
 *
 * <h2>왜 필요한가</h2>
 * JWT에는 회원 id만 있다(개인정보·권한을 토큰에 넣지 않는다). 그래서 인증 필터는 요청마다 "이 회원의 역할"을 DB에 물었다.
 * 홈을 5초마다 폴링하는 사용자는 5초마다 이 조회를 일으킨다. 부하 테스트에서 로그인 요청이 전체의 20%일 때
 * 용량이 3분의 1 줄었고, 이 조회가 그 비용의 일부였다(load/README.md 2026-09-28).
 *
 * <h2>왜 Redis가 아니라 메모리인가</h2>
 * 인증은 로그인한 모든 요청이 지나는 길이다. 여기에 네트워크 의존을 더하면 Redis가 느릴 때 전부 느려진다
 * (과부하에서 Redis 명령이 200ms 제한에 걸리는 것을 같은 측정에서 확인했다).
 * 태스크마다 따로 기억해도 {@link #TTL} 뒤에는 맞춰지므로 공유할 필요도 없다.
 *
 * <h2>60초 늦어도 되는 이유</h2>
 * <ul>
 *   <li><b>USER만 기억한다.</b> 운영자(STAFF·OWNER)는 기억하지 않고 매번 DB에서 읽는다. 권한 회수가 즉시 반영되고,
 *       기억해 둔 값으로는 어떤 경우에도 운영자 권한이 생기지 않는다</li>
 *   <li>작성 금지·매칭 차단은 인증 단계가 아니라 쓰기 시점에 DB로 확인한다(FR-AUTH-08). 여기와 무관하다</li>
 *   <li>삭제된 회원의 토큰은 최대 60초 더 통한다. 볼 수 있는 것은 본인 데이터뿐이고 그것도 이미 없다</li>
 * </ul>
 * 새로 운영자가 된 사람이 60초 동안 거부되는 일을 막으려고, 운영자 경로의 요청은 {@link #loadFresh}로 읽는다.
 */
public class UserRoleCache {

    /** 5초 폴링이면 12번 중 11번이 DB 없이 끝난다. 더 늘려도 60번 중 59번이라 얻는 것이 없고 위 지연만 길어진다 */
    static final Duration TTL = Duration.ofSeconds(60);
    /** 축제 참여 인원(수천 명)의 몇 배. 넘치면 오래된 것부터 버려지고 다음 요청이 DB에서 다시 읽는다 */
    private static final long MAX_ENTRIES = 10_000;

    private final UserRepository userRepository;
    private final Cache<Long, UserRole> roles;

    public UserRoleCache(UserRepository userRepository) {
        this(userRepository, Ticker.systemTicker());
    }

    // 시간 흐름을 테스트가 쥐도록 시계를 받는다
    UserRoleCache(UserRepository userRepository, Ticker ticker) {
        this.userRepository = userRepository;
        this.roles = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(TTL)
                .ticker(ticker)
                .build();
    }

    /**
     * 읽기·일반 쓰기 요청용. 기억해 둔 값이 있으면 DB를 읽지 않는다.
     * @return 회원이 없으면 비어 있다(기억하지 않으므로 다음 요청도 DB를 읽는다)
     */
    public Optional<UserRole> find(Long userId) {
        UserRole remembered = roles.getIfPresent(userId);
        if (remembered != null) {
            return Optional.of(remembered);
        }
        Optional<UserRole> role = userRepository.findRoleById(userId);
        role.filter(UserRole.USER::equals).ifPresent(user -> roles.put(userId, user));
        return role;
    }

    /** 운영자 경로용. 항상 DB에서 읽고, 방금 역할이 바뀐 회원이면 기억해 둔 옛 값도 버린다 */
    public Optional<UserRole> loadFresh(Long userId) {
        roles.invalidate(userId);
        return userRepository.findRoleById(userId);
    }
}
