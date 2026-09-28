package com.yufesta.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 역할을 기억해 DB 조회를 줄이되, 기억한 값으로 권한이 생기거나 남는 일이 없는지 고정한다
 */
@ExtendWith(MockitoExtension.class)
class UserRoleCacheTest {

    private static final long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    private final AtomicLong nanos = new AtomicLong();
    private UserRoleCache cache;

    @BeforeEach
    void setUp() {
        cache = new UserRoleCache(userRepository, nanos::get);
    }

    @Test
    void 같은_회원의_다음_요청은_DB를_읽지_않는다() {
        when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.of(UserRole.USER));

        // 5초 폴링 12번 = 60초
        for (int poll = 0; poll < 12; poll++) {
            assertThat(cache.find(USER_ID)).contains(UserRole.USER);
            advanceSeconds(4);
        }

        verify(userRepository, times(1)).findRoleById(USER_ID);
    }

    @Test
    void 기억한_지_60초가_지나면_다시_읽는다() {
        when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.of(UserRole.USER));

        cache.find(USER_ID);
        advanceSeconds(61);
        cache.find(USER_ID);

        verify(userRepository, times(2)).findRoleById(USER_ID);
    }

    @Test
    void 운영자_역할은_기억하지_않아_회수가_바로_반영된다() {
        when(userRepository.findRoleById(USER_ID))
                .thenReturn(Optional.of(UserRole.STAFF))
                .thenReturn(Optional.of(UserRole.USER));

        assertThat(cache.find(USER_ID)).contains(UserRole.STAFF);
        assertThat(cache.find(USER_ID)).contains(UserRole.USER);   // 허용 목록에서 빠진 뒤
    }

    @Test
    void 없는_회원은_기억하지_않는다() {
        when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.empty());

        assertThat(cache.find(USER_ID)).isEmpty();
        assertThat(cache.find(USER_ID)).isEmpty();

        verify(userRepository, times(2)).findRoleById(USER_ID);
    }

    @Test
    void 운영자_경로용_조회는_기억해_둔_값이_있어도_DB를_읽고_옛_값을_버린다() {
        when(userRepository.findRoleById(USER_ID))
                .thenReturn(Optional.of(UserRole.USER))
                .thenReturn(Optional.of(UserRole.STAFF));
        cache.find(USER_ID);   // USER로 기억됨

        // 방금 운영자가 된 회원
        assertThat(cache.loadFresh(USER_ID)).contains(UserRole.STAFF);

        // 일반 경로에서도 옛 USER가 남아 있지 않다
        when(userRepository.findRoleById(USER_ID)).thenReturn(Optional.of(UserRole.STAFF));
        assertThat(cache.find(USER_ID)).contains(UserRole.STAFF);
    }

    private void advanceSeconds(long seconds) {
        nanos.addAndGet(seconds * 1_000_000_000L);
    }
}
