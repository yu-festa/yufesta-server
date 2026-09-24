package com.yufesta.domain.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.yufesta.domain.user.enums.OAuthProvider;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.domain.user.service.UserService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClubAdminServiceTest {

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private UserService userService;

    @Mock
    private TimetableAdminService timetableAdminService;

    @Mock
    private ImageProcessor imageProcessor;

    @Mock
    private ImageStorage imageStorage;

    private final StorageProperties storageProperties = new StorageProperties(
            "local", "http://cdn.test/uploads",
            new StorageProperties.S3("", "ap-northeast-2"), new StorageProperties.Local("./uploads"));

    private ClubAdminService clubAdminService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        clubAdminService = new ClubAdminService(
                clubRepository, userService, timetableAdminService, imageProcessor, imageStorage, storageProperties);
    }

    @Test
    void 등록하면_로그인_운영자를_등록자로_기록한다() {
        User staff = User.builder()
                .provider(OAuthProvider.KAKAO).providerUserId("kakao-staff").role(UserRole.STAFF)
                .loginAt(LocalDateTime.of(2026, 9, 24, 12, 0))
                .build();
        ReflectionTestUtils.setField(staff, "id", 7L);
        when(userService.getUser(7L)).thenReturn(staff);
        when(clubRepository.save(any(Club.class))).thenAnswer(invocation -> {
            Club saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        AdminClubResponse response = clubAdminService.create(7L, createRequest());

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.createdById()).isEqualTo(7L);
        assertThat(response.instagramUrl()).isEqualTo("https://www.instagram.com/hipcom_yu");
    }

    @Test
    void 비로그인이면_UNAUTHORIZED를_던진다() {
        assertThatThrownBy(() -> clubAdminService.create(null, createRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(clubRepository, never()).save(any());
    }

    @Test
    void 수정하면_전체_필드를_바꾼다() {
        Club club = club(3L);
        when(clubRepository.findById(3L)).thenReturn(Optional.of(club));

        AdminClubResponse response = clubAdminService.update(3L, UpdateClubRequest.builder()
                .name("HIPCOM").intro("수정된 소개").genre("힙합").signatureSong("새 곡")
                .instagramUrl("https://instagram.com/hipcom_yu").sortOrder(9)
                .build());

        assertThat(response.intro()).isEqualTo("수정된 소개");
        assertThat(response.sortOrder()).isEqualTo(9);
    }

    @Test
    void 사진_업로드는_1600과_썸네일을_저장하고_photoUrl을_바꾼_뒤_이전_사진을_지운다() {
        Club club = club(3L);
        club.changePhoto("http://cdn.test/uploads/clubs/3/old-1600.jpg");
        when(clubRepository.findById(3L)).thenReturn(Optional.of(club));
        MockMultipartFile file = new MockMultipartFile("file", "hipcom.png", "image/png", new byte[] {9, 9});
        when(imageProcessor.process(new byte[] {9, 9}, "image/png")).thenReturn(new ProcessedImage(new byte[] {1}, new byte[] {2}));
        when(imageStorage.store(any(String.class), any(byte[].class), any(String.class)))
                .thenAnswer(invocation -> "http://cdn.test/uploads/" + invocation.getArgument(0));

        AdminClubResponse response = clubAdminService.uploadPhoto(3L, file);

        assertThat(response.photoUrl()).startsWith("http://cdn.test/uploads/clubs/3/").endsWith("-1600.jpg");
        verify(imageStorage).store(org.mockito.ArgumentMatchers.endsWith("-1600.jpg"), any(byte[].class), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        verify(imageStorage).store(org.mockito.ArgumentMatchers.endsWith("-thumb.jpg"), any(byte[].class), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        verify(imageStorage).delete("clubs/3/old-1600.jpg");
        verify(imageStorage).delete("clubs/3/old-thumb.jpg");
    }

    @Test
    void 빈_파일이면_IMAGE_INVALID이고_저장소를_부르지_않는다() {
        when(clubRepository.findById(3L)).thenReturn(Optional.of(club(3L)));

        assertThatThrownBy(() -> clubAdminService.uploadPhoto(3L, new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_INVALID);
        verify(imageStorage, never()).store(any(), any(), any());
    }

    @Test
    void 사진_삭제는_저장소_객체를_지우고_photoUrl을_비우며_외부_URL은_건드리지_않는다() {
        Club stored = club(3L);
        stored.changePhoto("http://cdn.test/uploads/clubs/3/old-1600.jpg");
        Club external = club(4L);
        external.changePhoto("https://example.com/photo.jpg");
        when(clubRepository.findById(3L)).thenReturn(Optional.of(stored));
        when(clubRepository.findById(4L)).thenReturn(Optional.of(external));

        clubAdminService.deletePhoto(3L);
        clubAdminService.deletePhoto(4L);

        assertThat(stored.getPhotoUrl()).isNull();
        assertThat(external.getPhotoUrl()).isNull();
        verify(imageStorage).delete("clubs/3/old-1600.jpg");
        verify(imageStorage).delete("clubs/3/old-thumb.jpg");
        verify(imageStorage, never()).delete(org.mockito.ArgumentMatchers.contains("example.com"));
    }

    @Test
    void 삭제는_타임테이블_연결을_먼저_끊고_지운다() {
        Club club = club(3L);
        when(clubRepository.findById(3L)).thenReturn(Optional.of(club));

        clubAdminService.delete(3L);

        InOrder order = inOrder(timetableAdminService, clubRepository);
        order.verify(timetableAdminService).detachClub(3L);
        order.verify(clubRepository).delete(club);
    }

    @Test
    void 없는_동아리면_CLUB_NOT_FOUND를_던진다() {
        when(clubRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubAdminService.delete(99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CLUB_NOT_FOUND);
        verify(timetableAdminService, never()).detachClub(any());
    }

    private static CreateClubRequest createRequest() {
        return CreateClubRequest.builder()
                .name("HIPCOM").intro("영남대학교 유일 힙합 동아리 HIPCOM").genre("힙합").signatureSong("최준현-거북당")
                .instagramUrl("https://www.instagram.com/hipcom_yu").sortOrder(3)
                .build();
    }

    private static Club club(Long id) {
        Club club = Club.builder().name("HIPCOM").intro("소개").sortOrder(3).build();
        ReflectionTestUtils.setField(club, "id", id);
        return club;
    }
}
