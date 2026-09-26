package com.yufesta.domain.lostitem.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.nickname.NicknameGenerator;
import com.yufesta.domain.lostitem.comment.dto.request.CreateLostItemCommentRequest;
import com.yufesta.domain.lostitem.comment.dto.request.UpdateLostItemCommentVisibilityRequest;
import com.yufesta.domain.lostitem.comment.dto.response.LostItemCommentResponse;
import com.yufesta.domain.lostitem.comment.entity.LostItemComment;
import com.yufesta.domain.lostitem.comment.entity.LostItemCommentAlias;
import com.yufesta.domain.lostitem.comment.repository.LostItemCommentAliasRepository;
import com.yufesta.domain.lostitem.comment.repository.LostItemCommentRepository;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
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
class LostItemCommentServiceTest {

    @Mock
    private LostItemRepository lostItemRepository;

    @Mock
    private LostItemCommentRepository commentRepository;

    @Mock
    private LostItemCommentAliasRepository aliasRepository;

    @Mock
    private UserService userService;

    @Mock
    private NicknameGenerator nicknameGenerator;

    @InjectMocks
    private LostItemCommentService lostItemCommentService;

    @Test
    void 비로그인_사용자가_최상위_댓글과_답글을_조회한다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        LostItemComment root = comment(10L, lostItem, null, 7L, "안내소에 맡겼어요.", "수줍은 펭귄", 10);
        LostItemComment reply = comment(11L, lostItem, root, 8L, "감사합니다.", "씩씩한 판다", 11);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(commentRepository.findAllByLostItem_IdAndHiddenFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(root, reply));

        List<LostItemCommentResponse> result = lostItemCommentService.getComments(null, 1L);

        assertThat(result).singleElement()
                .extracting(
                        LostItemCommentResponse::content,
                        LostItemCommentResponse::mine,
                        LostItemCommentResponse::postAuthor
                )
                .containsExactly("안내소에 맡겼어요.", false, true);
        assertThat(result.get(0).replies()).singleElement()
                .extracting(LostItemCommentResponse::content, LostItemCommentResponse::displayName)
                .containsExactly("감사합니다.", "씩씩한 판다");
    }

    @Test
    void 로그인_사용자가_글_단위_익명_닉네임으로_최상위_댓글을_작성한다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        User author = user(8L);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(userService.getUser(8L)).thenReturn(author);
        when(aliasRepository.findByLostItem_IdAndUser_Id(1L, 8L)).thenReturn(Optional.empty());
        when(aliasRepository.existsByLostItem_IdAndDisplayName(1L, "씩씩한 판다")).thenReturn(false);
        when(nicknameGenerator.generate()).thenReturn("씩씩한 판다");
        when(aliasRepository.save(any(LostItemCommentAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.save(any(LostItemComment.class))).thenAnswer(invocation -> {
            LostItemComment comment = invocation.getArgument(0);
            ReflectionTestUtils.setField(comment, "id", 10L);
            ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.of(2026, 10, 2, 14, 0));
            return comment;
        });

        LostItemCommentResponse result = lostItemCommentService.create(8L, 1L, request(" 안내소에 맡겼어요. "));

        assertThat(result)
                .extracting(LostItemCommentResponse::content, LostItemCommentResponse::displayName, LostItemCommentResponse::mine)
                .containsExactly("안내소에 맡겼어요.", "씩씩한 판다", true);
        ArgumentCaptor<LostItemComment> captor = ArgumentCaptor.forClass(LostItemComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue()).extracting(LostItemComment::isTopLevel, LostItemComment::getAuthor)
                .containsExactly(true, author);
    }

    @Test
    void 같은_분실물_글에는_기존_익명_닉네임을_재사용한다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        User author = user(8L);
        LostItemCommentAlias alias = LostItemCommentAlias.builder()
                .lostItem(lostItem).user(author).displayName("씩씩한 판다").build();
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(userService.getUser(8L)).thenReturn(author);
        when(aliasRepository.findByLostItem_IdAndUser_Id(1L, 8L)).thenReturn(Optional.of(alias));
        when(commentRepository.save(any(LostItemComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LostItemCommentResponse result = lostItemCommentService.create(8L, 1L, request("확인했습니다."));

        assertThat(result.displayName()).isEqualTo("씩씩한 판다");
        verify(aliasRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void 로그인_사용자가_최상위_댓글에_답글을_작성한다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        LostItemComment root = comment(10L, lostItem, null, 7L, "안내소에 맡겼어요.", "수줍은 펭귄", 10);
        User author = user(8L);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(root));
        when(userService.getUser(8L)).thenReturn(author);
        when(aliasRepository.findByLostItem_IdAndUser_Id(1L, 8L)).thenReturn(Optional.empty());
        when(aliasRepository.existsByLostItem_IdAndDisplayName(1L, "씩씩한 판다")).thenReturn(false);
        when(nicknameGenerator.generate()).thenReturn("씩씩한 판다");
        when(aliasRepository.save(any(LostItemCommentAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.save(any(LostItemComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LostItemCommentResponse result = lostItemCommentService.reply(8L, 1L, 10L, request("감사합니다."));

        assertThat(result.parentId()).isEqualTo(10L);
        assertThat(result.content()).isEqualTo("감사합니다.");
    }

    @Test
    void 답글에는_다시_답글을_작성할_수_없다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        LostItemComment root = comment(10L, lostItem, null, 7L, "댓글", "수줍은 펭귄", 10);
        LostItemComment reply = comment(11L, lostItem, root, 8L, "답글", "씩씩한 판다", 11);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(commentRepository.findById(11L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> lostItemCommentService.reply(9L, 1L, 11L, request("대답")))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.LOST_ITEM_COMMENT_REPLY_NOT_ALLOWED);
    }

    @Test
    void 작성자는_자신의_댓글을_소프트_삭제한다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        LostItemComment comment = comment(10L, lostItem, null, 8L, "삭제할 댓글", "씩씩한 판다", 10);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        lostItemCommentService.delete(8L, 1L, 10L);

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getDisplayContent()).isEqualTo(LostItemComment.DELETED_CONTENT);
    }

    @Test
    void 다른_사용자는_댓글을_삭제할_수_없다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        LostItemComment comment = comment(10L, lostItem, null, 8L, "삭제할 댓글", "씩씩한 판다", 10);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> lostItemCommentService.delete(9L, 1L, 10L))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 운영자는_댓글을_숨기고_복구한다() {
        LostItem lostItem = lostItem(1L, 7L, false);
        LostItemComment comment = comment(10L, lostItem, null, 8L, "댓글", "씩씩한 판다", 10);
        when(lostItemRepository.findById(1L)).thenReturn(Optional.of(lostItem));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        lostItemCommentService.updateVisibility(1L, 10L, new UpdateLostItemCommentVisibilityRequest(true));
        assertThat(comment.isHidden()).isTrue();

        lostItemCommentService.updateVisibility(1L, 10L, new UpdateLostItemCommentVisibilityRequest(false));
        assertThat(comment.isHidden()).isFalse();
    }

    private static CreateLostItemCommentRequest request(String content) {
        return new CreateLostItemCommentRequest(content);
    }

    private static LostItem lostItem(Long id, Long authorId, boolean hidden) {
        User author = user(authorId);
        LostItem lostItem = LostItem.builder()
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .displayName("수줍은 펭귄")
                .author(author)
                .build();
        ReflectionTestUtils.setField(lostItem, "id", id);
        if (hidden) {
            lostItem.hide();
        }
        return lostItem;
    }

    private static LostItemComment comment(
            Long id,
            LostItem lostItem,
            LostItemComment parent,
            Long authorId,
            String content,
            String displayName,
            int minute
    ) {
        LostItemComment comment = LostItemComment.builder()
                .lostItem(lostItem)
                .parent(parent)
                .author(user(authorId))
                .content(content)
                .displayName(displayName)
                .build();
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.of(2026, 10, 2, 14, minute));
        return comment;
    }

    private static User user(Long id) {
        User user = User.builder()
                .provider(OAuthProvider.GOOGLE)
                .providerUserId("test-user-" + id)
                .role(UserRole.USER)
                .loginAt(LocalDateTime.of(2026, 10, 1, 0, 0))
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
