package com.yufesta.domain.club.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.club.dto.request.CreateClubRequest;
import com.yufesta.domain.club.dto.request.UpdateClubRequest;
import com.yufesta.domain.club.dto.response.AdminClubResponse;
import com.yufesta.domain.club.entity.Club;
import com.yufesta.domain.club.repository.ClubRepository;
import com.yufesta.domain.timetable.service.TimetableAdminService;
import com.yufesta.domain.user.entity.User;
import com.yufesta.domain.user.service.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ClubAdminService(
            ClubRepository clubRepository,
            UserService userService,
            TimetableAdminService timetableAdminService
    ) {
        this.clubRepository = clubRepository;
        this.userService = userService;
        this.timetableAdminService = timetableAdminService;
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
                .photoUrl(request.photoUrl())
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
                request.photoUrl(),
                request.sortOrder()
        );
        return AdminClubResponse.from(club);
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
