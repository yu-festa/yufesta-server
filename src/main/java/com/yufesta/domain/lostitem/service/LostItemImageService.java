package com.yufesta.domain.lostitem.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.storage.ImageProcessor;
import com.yufesta.common.storage.ImageStorage;
import com.yufesta.common.storage.ProcessedImage;
import com.yufesta.common.storage.StorageProperties;
import com.yufesta.domain.lostitem.dto.response.LostItemImageResponse;
import com.yufesta.domain.lostitem.entity.LostItem;
import com.yufesta.domain.lostitem.entity.LostItemImage;
import com.yufesta.domain.lostitem.repository.LostItemImageRepository;
import com.yufesta.domain.lostitem.repository.LostItemRepository;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 분실물 게시글 이미지 한 장의 저장·삭제를 처리 */
@Service
@Transactional(readOnly = true)
public class LostItemImageService {

    private final LostItemRepository lostItemRepository;
    private final LostItemImageRepository lostItemImageRepository;
    private final ImageProcessor imageProcessor;
    private final ImageStorage imageStorage;
    private final StorageProperties storageProperties;

    public LostItemImageService(
            LostItemRepository lostItemRepository,
            LostItemImageRepository lostItemImageRepository,
            ImageProcessor imageProcessor,
            ImageStorage imageStorage,
            StorageProperties storageProperties
    ) {
        this.lostItemRepository = lostItemRepository;
        this.lostItemImageRepository = lostItemImageRepository;
        this.imageProcessor = imageProcessor;
        this.imageStorage = imageStorage;
        this.storageProperties = storageProperties;
    }

    /**
     * 작성자 본인의 공개 분실물 게시글에 이미지 한 장을 저장한다(FR-LF-10).
     * <p>게시글 행을 잠가 동시 업로드도 한 장 제약으로 직렬화한다.
     * @throws CustomException UNAUTHORIZED, LOST_ITEM_NOT_FOUND, FORBIDDEN, LOST_ITEM_IMAGE_ALREADY_EXISTS,
     *         IMAGE_UNSUPPORTED_TYPE, IMAGE_INVALID
     */
    @Transactional
    public LostItemImageResponse upload(Long userId, Long lostItemId, MultipartFile file) {
        LostItem lostItem = getLostItemForUpdate(lostItemId);
        requireEditableBy(userId, lostItem);
        if (lostItemImageRepository.existsByLostItemId(lostItemId)) {
            throw new CustomException(ErrorCode.LOST_ITEM_IMAGE_ALREADY_EXISTS);
        }

        ProcessedImage image = imageProcessor.process(bytesOf(file), file.getContentType());
        String base = "lost-items/" + lostItemId + "/" + UUID.randomUUID();
        String imageUrl = imageStorage.store(base + "-1600.jpg", image.large(), ProcessedImage.CONTENT_TYPE);
        String thumbnailUrl = imageStorage.store(base + "-thumb.jpg", image.thumbnail(), ProcessedImage.CONTENT_TYPE);
        LostItemImage saved = lostItemImageRepository.save(LostItemImage.builder()
                .lostItem(lostItem)
                .imageUrl(imageUrl)
                .thumbnailUrl(thumbnailUrl)
                .build());
        return LostItemImageResponse.from(saved);
    }

    /**
     * 작성자 본인이 등록한 분실물 이미지와 저장소 객체를 함께 지운다(FR-LF-10).
     * @throws CustomException UNAUTHORIZED, LOST_ITEM_NOT_FOUND, LOST_ITEM_IMAGE_NOT_FOUND, FORBIDDEN
     */
    @Transactional
    public void delete(Long userId, Long lostItemId, Long imageId) {
        LostItem lostItem = getLostItemForUpdate(lostItemId);
        requireEditableBy(userId, lostItem);
        LostItemImage image = lostItemImageRepository.findByIdAndLostItemId(imageId, lostItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOST_ITEM_IMAGE_NOT_FOUND));
        deleteStored(image.getImageUrl());
        deleteStored(image.getThumbnailUrl());
        lostItemImageRepository.delete(image);
    }

    private LostItem getLostItemForUpdate(Long lostItemId) {
        return lostItemRepository.findByIdForUpdate(lostItemId)
                .orElseThrow(() -> new CustomException(ErrorCode.LOST_ITEM_NOT_FOUND));
    }

    private static void requireEditableBy(Long userId, LostItem lostItem) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        if (lostItem.isHidden() || !lostItem.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void deleteStored(String url) {
        Optional<String> key = storageProperties.keyOf(url);
        key.ifPresent(imageStorage::delete);
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.IMAGE_INVALID);
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.IMAGE_INVALID);
        }
    }
}
