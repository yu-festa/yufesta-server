package com.yufesta.domain.lostitem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.storage.ImageProcessor;
import com.yufesta.common.storage.ImageStorage;
import com.yufesta.common.storage.ProcessedImage;
import com.yufesta.common.storage.StorageProperties;
import com.yufesta.domain.lostitem.dto.response.LostItemImageResponse;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.entity.LostItemImage;
import com.yufesta.domain.lostitem.enums.LostItemKind;
import com.yufesta.domain.lostitem.repository.LostItemImageRepository;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LostItemImageServiceTest {

    @Mock
    private LostItemRepository lostItemRepository;

    @Mock
    private LostItemImageRepository lostItemImageRepository;

    @Mock
    private ImageProcessor imageProcessor;

    @Mock
    private ImageStorage imageStorage;

    private final StorageProperties storageProperties = new StorageProperties(
            "local", "http://cdn.test/uploads",
            new StorageProperties.S3("", "ap-northeast-2"), new StorageProperties.Local("./uploads"));

    private LostItemImageService lostItemImageService;

    @BeforeEach
    void setUp() {
        lostItemImageService = new LostItemImageService(
                lostItemRepository,
                lostItemImageRepository,
                imageProcessor,
                imageStorage,
                storageProperties
        );
    }

    @Test
    void 작성자는_본인_분실물_글에_이미지_한장을_등록한다() {
        LostItem lostItem = ownedLostItem(1L, 7L);
        MockMultipartFile file = new MockMultipartFile("file", "wallet.png", "image/png", new byte[] {1, 2});
        when(lostItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lostItem));
        when(lostItemImageRepository.existsByLostItemId(1L)).thenReturn(false);
        when(imageProcessor.process(new byte[] {1, 2}, "image/png"))
                .thenReturn(new ProcessedImage(new byte[] {3}, new byte[] {4}));
        when(imageStorage.store(any(String.class), any(byte[].class), any(String.class)))
                .thenAnswer(invocation -> "http://cdn.test/uploads/" + invocation.getArgument(0));
        when(lostItemImageRepository.save(any(LostItemImage.class))).thenAnswer(invocation -> {
            LostItemImage image = invocation.getArgument(0);
            ReflectionTestUtils.setField(image, "id", 3L);
            return image;
        });

        LostItemImageResponse response = lostItemImageService.upload(7L, 1L, file);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.imageUrl()).startsWith("http://cdn.test/uploads/lost-items/1/").endsWith("-1600.jpg");
        assertThat(response.thumbnailUrl()).startsWith("http://cdn.test/uploads/lost-items/1/").endsWith("-thumb.jpg");
        ArgumentCaptor<LostItemImage> captor = ArgumentCaptor.forClass(LostItemImage.class);
        verify(lostItemImageRepository).save(captor.capture());
        assertThat(captor.getValue().getLostItem()).isSameAs(lostItem);
        verify(imageStorage).store(org.mockito.ArgumentMatchers.endsWith("-1600.jpg"), any(byte[].class), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        verify(imageStorage).store(org.mockito.ArgumentMatchers.endsWith("-thumb.jpg"), any(byte[].class), org.mockito.ArgumentMatchers.eq("image/jpeg"));
    }

    @Test
    void 이미지가_이미_있으면_LOST_ITEM_IMAGE_ALREADY_EXISTS를_던진다() {
        when(lostItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ownedLostItem(1L, 7L)));
        when(lostItemImageRepository.existsByLostItemId(1L)).thenReturn(true);

        assertThatThrownBy(() -> lostItemImageService.upload(7L, 1L, imageFile()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.LOST_ITEM_IMAGE_ALREADY_EXISTS);
        verify(imageProcessor, never()).process(any(), any());
        verify(imageStorage, never()).store(any(), any(), any());
    }

    @Test
    void 다른_작성자는_이미지를_등록할_수_없다() {
        when(lostItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ownedLostItem(1L, 7L)));

        assertThatThrownBy(() -> lostItemImageService.upload(8L, 1L, imageFile()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(lostItemImageRepository, never()).existsByLostItemId(any());
    }

    @Test
    void 숨김_분실물_글에는_이미지를_등록할_수_없다() {
        LostItem hiddenLostItem = ownedLostItem(1L, 7L);
        hiddenLostItem.hide();
        when(lostItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(hiddenLostItem));

        assertThatThrownBy(() -> lostItemImageService.upload(7L, 1L, imageFile()))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        verify(lostItemImageRepository, never()).existsByLostItemId(any());
    }

    @Test
    void 작성자는_이미지를_삭제하면_본문용과_썸네일_객체를_함께_지운다() {
        LostItem lostItem = ownedLostItem(1L, 7L);
        LostItemImage image = LostItemImage.builder()
                .lostItem(lostItem)
                .imageUrl("http://cdn.test/uploads/lost-items/1/wallet-1600.jpg")
                .thumbnailUrl("http://cdn.test/uploads/lost-items/1/wallet-thumb.jpg")
                .build();
        ReflectionTestUtils.setField(image, "id", 3L);
        when(lostItemRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lostItem));
        when(lostItemImageRepository.findByIdAndLostItemId(3L, 1L)).thenReturn(Optional.of(image));

        lostItemImageService.delete(7L, 1L, 3L);

        verify(imageStorage).delete("lost-items/1/wallet-1600.jpg");
        verify(imageStorage).delete("lost-items/1/wallet-thumb.jpg");
        verify(lostItemImageRepository).delete(image);
    }

    private static MockMultipartFile imageFile() {
        return new MockMultipartFile("file", "wallet.png", "image/png", new byte[] {1, 2});
    }

    private static LostItem ownedLostItem(Long id, Long authorId) {
        User author = User.builder()
                .provider(OAuthProvider.KAKAO)
                .providerUserId("kakao-" + authorId)
                .role(UserRole.USER)
                .loginAt(LocalDateTime.of(2026, 9, 26, 12, 0))
                .build();
        ReflectionTestUtils.setField(author, "id", authorId);
        LostItem lostItem = LostItem.builder()
                .kind(LostItemKind.FOUND)
                .description("검은색 카드지갑")
                .placeText("중앙도서관 앞")
                .displayName("수줍은 펭귄")
                .author(author)
                .build();
        ReflectionTestUtils.setField(lostItem, "id", id);
        return lostItem;
    }
}
