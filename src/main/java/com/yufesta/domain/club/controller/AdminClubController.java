package com.yufesta.domain.club.controller;

import com.yufesta.common.response.ApiResponse;
import com.yufesta.domain.club.controller.api.AdminClubApi;
import com.yufesta.domain.club.dto.request.CreateClubRequest;
import com.yufesta.domain.club.dto.request.UpdateClubRequest;
import com.yufesta.domain.club.dto.response.AdminClubResponse;
import com.yufesta.domain.club.service.ClubAdminService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 운영자 라인업 관리 API. 명세는 AdminClubApi */
@RestController
public class AdminClubController implements AdminClubApi {

    private final ClubAdminService clubAdminService;

    public AdminClubController(ClubAdminService clubAdminService) {
        this.clubAdminService = clubAdminService;
    }

    @Override
    public ApiResponse<List<AdminClubResponse>> getClubs() {
        return ApiResponse.success(clubAdminService.getClubs());
    }

    @Override
    public ResponseEntity<ApiResponse<AdminClubResponse>> create(Long userId, CreateClubRequest request) {
        AdminClubResponse response = clubAdminService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(HttpStatus.CREATED, "동아리를 등록했습니다.", response));
    }

    @Override
    public ApiResponse<AdminClubResponse> update(Long clubId, UpdateClubRequest request) {
        return ApiResponse.success("동아리를 수정했습니다.", clubAdminService.update(clubId, request));
    }

    @Override
    public ApiResponse<AdminClubResponse> uploadPhoto(Long clubId, MultipartFile file) {
        return ApiResponse.success("대표 사진을 올렸습니다.", clubAdminService.uploadPhoto(clubId, file));
    }

    @Override
    public void deletePhoto(Long clubId) {
        clubAdminService.deletePhoto(clubId);
    }

    @Override
    public void delete(Long clubId) {
        clubAdminService.delete(clubId);
    }
}
