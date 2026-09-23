package com.yufesta.domain.auth.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.auth.dto.response.AuthMeResponse;
import com.yufesta.domain.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 상태 조회
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserService userService;

    public AuthService(UserService userService) {
        this.userService = userService;
    }

    /**
     * 현재 로그인 사용자의 역할을 반환한다. 프론트 로그인 게이트가 사용한다.
     * @throws CustomException UNAUTHORIZED(비로그인), USER_NOT_FOUND
     */
    public AuthMeResponse getMe(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return AuthMeResponse.of(userService.getRole(userId));
    }
}
