package com.yufesta.domain.club.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.storage.ImageProcessor;
import com.yufesta.common.storage.ImageStorage;
import com.yufesta.common.storage.ProcessedImage;
import com.yufesta.common.storage.StorageProperties;
import com.yufesta.domain.club.dto.request.CreateClubRequest;
import com.yufesta.domain.club.dto.request.UpdateClubRequest;
import com.yufesta.domain.club.dto.response.AdminClubResponse;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.club.repository.ClubRepository;
import com.yufesta.domain.timetable.service.TimetableAdminService;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 운영자 라인업 관리(FR-ADM-07, FR-LU-04). 조회 서비스(ClubService)와 분리해 타임테이블과의 의존이 한 방향이 되게 한다:
 * ClubService → TimetableService, TimetableAdminService → ClubService, ClubAdminService → TimetableAdminService
 */
@Service
@Transactional(readOnly = true)
public class ClubAdminService {

    private final ClubRepository clubRepository;
    private final UserService userService;
    private final TimetableAdminService timetableAdminService;
    private final ImageProcessor imageProcessor;
    private final ImageStorage imageStorage;
    private final StorageProperties storageProperties;

    public ClubAdminService(
            ClubRepository clubRepository,
            UserService userService,
            TimetableAdminService timetableAdminService,
            ImageProcessor imageProcessor,
            ImageStorage imageStorage,
            StorageProperties storageProperties
    ) {
        this.clubRepository = clubRepository;
        this.userService = userService;
        this.timetableAdminService = timetableAdminService;
        this.imageProcessor = imageProcessor;
        this.imageStorage = imageStorage;
        this.storageProperties = storageProperties;
    }

    /** 운영자 화면용 전체 목록. 표시 순서·이름순 */
    public List<AdminClubResponse> getClubs() {
        return clubRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(AdminClubResponse::from)
                .toList();
    }

    /**
     * 동아리 카드를 등록하고 등록 운영자를 기록한다(FR-ADM-07).
     * @throws CustomException UNAUTHORIZED, USER_NOT_FOUND
     */
    @Transactional
    public AdminClubResponse create(Long userId, CreateClubRequest request) {
        User createdBy = requireUser(userId);
        Club club = Club.builder()
                .name(request.name())
                .intro(request.intro())
                .genre(request.genre())
                .signatureSong(request.signatureSong())
                .instagramUrl(request.instagramUrl())
                .sortOrder(request.sortOrder())
                .createdBy(createdBy)
                .build();
        return AdminClubResponse.from(clubRepository.save(club));
    }

    /**
     * 동아리 카드를 수정한다(FR-ADM-07).
     * @throws CustomException CLUB_NOT_FOUND
     */
    @Transactional
    public AdminClubResponse update(Long clubId, UpdateClubRequest request) {
        Club club = getClubOrThrow(clubId);
        club.update(
                request.name(),
                request.intro(),
                request.genre(),
                request.signatureSong(),
                request.instagramUrl(),
                request.sortOrder()
        );
        return AdminClubResponse.from(club);
    }

    /**
     * 대표 사진을 올린다(FR-LU-04, FR-PH-05). 긴 변 1600px·썸네일 400px JPEG로 줄여 저장하고 photo_url을 1600 URL로 바꾼다.
     * 새 사진을 먼저 저장한 뒤 이전 사진을 지우므로 실패해도 이전 사진이 남는다.
     * @throws CustomException CLUB_NOT_FOUND, IMAGE_UNSUPPORTED_TYPE, IMAGE_INVALID
     */
    @Transactional
    public AdminClubResponse uploadPhoto(Long clubId, MultipartFile file) {
        Club club = getClubOrThrow(clubId);
        ProcessedImage image = imageProcessor.process(bytesOf(file), file.getContentType());

        String base = "clubs/" + clubId + "/" + UUID.randomUUID();
        String largeUrl = imageStorage.store(base + "-1600.jpg", image.large(), ProcessedImage.CONTENT_TYPE);
        imageStorage.store(base + "-thumb.jpg", image.thumbnail(), ProcessedImage.CONTENT_TYPE);

        String previous = club.getPhotoUrl();
        club.changePhoto(largeUrl);
        deleteStored(previous);
        return AdminClubResponse.from(club);
    }

    /**
     * 대표 사진을 지우고 photo_url을 비운다.
     * @throws CustomException CLUB_NOT_FOUND
     */
    @Transactional
    public void deletePhoto(Long clubId) {
        Club club = getClubOrThrow(clubId);
        String previous = club.getPhotoUrl();
        club.changePhoto(null);
        deleteStored(previous);
    }

    /**
     * 동아리를 삭제한다. 연결된 공연은 남기고 club만 비운다(타임테이블 서비스가 먼저 연결을 끊는다).
     * @throws CustomException CLUB_NOT_FOUND
     */
    @Transactional
    public void delete(Long clubId) {
        Club club = getClubOrThrow(clubId);
        // H2 테스트 스키마엔 FK ON DELETE SET NULL이 없으므로 DB에 맡기지 않고 코드에서 끊는다
        timetableAdminService.detachClub(clubId);
        clubRepository.delete(club);
    }

    // 저장소 URL이면 1600·썸네일 두 객체를 지운다. 외부 URL(초기 데이터 등)은 건드리지 않는다
    private void deleteStored(String url) {
        Optional<String> key = storageProperties.keyOf(url);
        if (key.isEmpty()) {
            return;
        }
        imageStorage.delete(key.get());
        imageStorage.delete(key.get().replace("-1600.jpg", "-thumb.jpg"));
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

    private Club getClubOrThrow(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLUB_NOT_FOUND));
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return userService.getUser(userId);
    }
}
