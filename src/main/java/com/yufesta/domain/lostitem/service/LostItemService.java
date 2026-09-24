package com.yufesta.domain.lostitem.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분실물 게시글 공개 조회와 로그인이 필요한 작성을 처리 */
@Service
@Transactional(readOnly = true)
public class LostItemService {

    private final LostItemRepository lostItemRepository;
    private final UserService userService;
    private final NicknameGenerator nicknameGenerator;

    public LostItemService(
            LostItemRepository lostItemRepository,
            UserService userService,
            NicknameGenerator nicknameGenerator
    ) {
        this.lostItemRepository = lostItemRepository;
        this.userService = userService;
        this.nicknameGenerator = nicknameGenerator;
    }

    /** 숨김 처리되지 않은 분실물 게시글을 최신순으로 조회한다(FR-LF-01). */
    public List<LostItemResponse> getLostItems(int size) {
        return lostItemRepository.findAllByHiddenFalseOrderByCreatedAtDesc(PageRequest.of(0, size))
                .stream()
                .map(LostItemResponse::from)
                .toList();
    }

    /**
     * 로그인 사용자의 분실물 게시글을 자동 생성 닉네임과 함께 저장한다(FR-LF-02, FR-AN-01~03).
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND
     */
    @Transactional
    public LostItemResponse create(Long userId, CreateLostItemRequest request) {
        User user = requireUser(userId);
        LostItem lostItem = LostItem.builder()
                .kind(request.kind())
                .description(request.description().trim())
                .placeText(request.placeText().trim())
                .occurredAt(request.occurredAt())
                .displayName(nicknameGenerator.generate())
                .author(user)
                .build();
        return LostItemResponse.from(lostItemRepository.save(lostItem));
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userService.getUser(userId);
    }
}
