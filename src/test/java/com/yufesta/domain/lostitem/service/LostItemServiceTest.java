package com.yufesta.domain.lostitem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import com.yufesta.domain.lostitem.dto.request.CreateOfficialLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.CreateLostItemRequest;
import com.yufesta.domain.lostitem.dto.request.UpdateLostItemVisibilityRequest;
import com.yufesta.domain.lostitem.dto.response.LostItemResponse;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.enums.LostItemStatus;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LostItemServiceTest {

    @Mock
    private LostItemRepository lostItemRepository;

    @Mock
    private UserService userService;

    @Mock
    private NicknameGenerator nicknameGenerator;

    @InjectMocks
    private LostItemService lostItemService;

    @Test
    void 최신순_공개_분실물_목록을_조회한다() {
        LostItem lostItem = lostItem(1L, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(lostItemRepository.findAllByHiddenFalseOrderByCreatedAtDesc(any())).thenReturn(List.of(lostItem));

        List<LostItemResponse> result = lostItemService.getLostItems(20);

        assertThat(result)
                .extracting(LostItemResponse::kind, LostItemResponse::description, LostItemResponse::status)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(LostItemKind.FOUND, "검은색 카드지갑", LostItemStatus.OPEN));
    }

    @Test
    void 로그인_사용자가_분실물_게시글을_작성한다() {
        User user = org.mockito.Mockito.mock(User.class);
        when(userService.getUser(7L)).thenReturn(user);
        when(nicknameGenerator.generate()).thenReturn("씩씩한 판다");
        when(lostItemRepository.save(any(LostItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LostItemResponse result = lostItemService.create(7L, request());

        assertThat(result)
                .extracting(LostItemResponse::description, LostItemResponse::placeText, LostItemResponse::displayName)
                .containsExactly("검은색 카드지갑", "중앙도서관 앞", "씩씩한 판다");
        ArgumentCaptor<LostItem> captor = ArgumentCaptor.forClass(LostItem.class);
        verify(lostItemRepository).save(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        LostItem::getKind,
                        LostItem::getStatus,
                        LostItem::getAuthor,
                        LostItem::isOfficial,
                        LostItem::getModerationStatus,
                        LostItem::isHidden
                )
                .containsExactly(LostItemKind.FOUND, LostItemStatus.OPEN, user, false, ModerationStatus.PASSED, false);
    }

    @Test
    void 비로그인_사용자가_분실물_게시글을_작성하면_UNAUTHORIZED를_던진다() {
        assertThatThrownBy(() -> lostItemService.create(null, request()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 작성자_본인이_분실물_게시글을_해결_처리한다() {
        LostItem lostItem = ownedLostItem(1L, 7L, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));

        LostItemResponse result = lostItemService.resolve(7L, 1L);

        assertThat(result.status()).isEqualTo(LostItemStatus.RESOLVED);
    }

    @Test
    void 작성자가_아닌_사용자가_분실물_게시글을_삭제하면_FORBIDDEN을_던진다() {
        LostItem lostItem = ownedLostItem(1L, 7L, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));

        assertThatThrownBy(() -> lostItemService.delete(8L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 작성자_본인이_분실물_게시글을_삭제하면_숨김_처리한다() {
        LostItem lostItem = ownedLostItem(1L, 7L, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));

        lostItemService.delete(7L, 1L);

        assertThat(lostItem.isHidden()).isTrue();
    }

    @Test
    void 운영자가_안내소_습득물을_공식_게시글로_등록한다() {
        User admin = org.mockito.Mockito.mock(User.class);
        when(userService.getUser(7L)).thenReturn(admin);
        when(lostItemRepository.save(any(LostItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LostItemResponse result = lostItemService.createOfficial(7L, officialRequest());

        assertThat(result)
                .extracting(LostItemResponse::kind, LostItemResponse::displayName)
                .containsExactly(LostItemKind.FOUND, LostItem.OFFICIAL_DISPLAY_NAME);
        ArgumentCaptor<LostItem> captor = ArgumentCaptor.forClass(LostItem.class);
        verify(lostItemRepository).save(captor.capture());
        assertThat(captor.getValue())
                .extracting(LostItem::isOfficial, LostItem::getAuthor, LostItem::getModerationStatus)
                .containsExactly(true, admin, ModerationStatus.PASSED);
    }

    @Test
    void 운영자가_분실물_게시글을_해결_처리한다() {
        LostItem lostItem = lostItem(1L, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));

        LostItemResponse result = lostItemService.resolveByAdmin(3L, 1L);

        assertThat(result.status()).isEqualTo(LostItemStatus.RESOLVED);
    }

    @Test
    void 운영자가_일반_분실물_게시글을_숨기고_복구한다() {
        LostItem lostItem = lostItem(1L, LocalDateTime.of(2026, 10, 2, 14, 0));
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));

        lostItemService.updateVisibility(3L, 1L, new UpdateLostItemVisibilityRequest(true));
        assertThat(lostItem.isHidden()).isTrue();

        lostItemService.updateVisibility(3L, 1L, new UpdateLostItemVisibilityRequest(false));
        assertThat(lostItem.isHidden()).isFalse();
    }

    @Test
    void 운영자는_공식_안내소_게시글을_숨기지_않는다() {
        User admin = org.mockito.Mockito.mock(User.class);
        LostItem officialLostItem = LostItem.official(
                admin,
                "검은색 카드지갑",
                "중앙도서관 앞",
                LocalDateTime.of(2026, 10, 2, 14, 0)
        );
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(officialLostItem));

        assertThatThrownBy(() -> lostItemService.updateVisibility(3L, 1L, new UpdateLostItemVisibilityRequest(true)))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static CreateLostItemRequest request() {
        return CreateLostItemRequest.builder()
                .kind(LostItemKind.FOUND)
                .description(" 검은색 카드지갑 ")
                .placeText(" 중앙도서관 앞 ")
                .occurredAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }

    private static CreateOfficialLostItemRequest officialRequest() {
        return CreateOfficialLostItemRequest.builder()
                .description(" 검은색 카드지갑 ")
                .placeText(" 중앙도서관 앞 ")
                .occurredAt(LocalDateTime.of(2026, 10, 2, 14, 0))
                .build();
    }

    private static LostItem lostItem(Long id, LocalDateTime createdAt) {
        return buildLostItem(id, org.mockito.Mockito.mock(User.class), createdAt);
    }

    private static LostItem ownedLostItem(Long id, Long authorId, LocalDateTime createdAt) {
        User author = org.mockito.Mockito.mock(User.class);
        when(author.getId()).thenReturn(authorId);
        return buildLostItem(id, author, createdAt);
    }

    private static LostItem buildLostItem(Long id, User author, LocalDateTime createdAt) {
        LostItem lostItem = LostItem.builder()
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .displayName("수줍은 펭귄")
                .author(author)
                .build();
        ReflectionTestUtils.setField(lostItem, "id", id);
        ReflectionTestUtils.setField(lostItem, "createdAt", createdAt);
        return lostItem;
    }
}
