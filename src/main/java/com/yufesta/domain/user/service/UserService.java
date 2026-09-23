package com.yufesta.domain.user.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 조회. 다른 도메인은 UserRepository 대신 이 서비스를 통해 회원 정보를 읽는다
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 회원 엔티티를 조회한다. 다른 도메인이 연관(예: 신청의 user)을 맺을 때 쓴다.
     * @throws CustomException USER_NOT_FOUND
     */
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 회원 행을 PESSIMISTIC_WRITE로 잠그고 반환한다. 집계 후 상태를 바꾸는 작업(신고 누적 제재)을 한 줄로 세우는 용도(§5).
     * 쓰기 트랜잭션 안에서만 호출한다.
     * @throws CustomException USER_NOT_FOUND
     */
    @Transactional
    public User getUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 회원의 역할을 조회한다.
     * @throws CustomException USER_NOT_FOUND
     */
    public UserRole getRole(Long userId) {
        return userRepository.findById(userId)
                .map(User::getRole)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
