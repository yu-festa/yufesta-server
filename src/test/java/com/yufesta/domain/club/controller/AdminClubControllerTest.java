package com.yufesta.domain.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.club.dto.request.CreateClubRequest;
import com.yufesta.domain.club.dto.request.UpdateClubRequest;
import com.yufesta.domain.club.dto.response.AdminClubResponse;
import com.yufesta.domain.club.service.ClubAdminService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminClubController.class)
class AdminClubControllerTest extends ControllerTestSupport {

    private static final String CLUB_JSON = """
            {"name":"HIPCOM","intro":"영남대학교 유일 힙합 동아리 HIPCOM","genre":"힙합","signatureSong":"최준현-거북당",
             "instagramUrl":"https://www.instagram.com/hipcom_yu","sortOrder":3}
            """;

    @MockitoBean
    private ClubAdminService clubAdminService;

    @Test
    @WithMockLoginUser(id = 7L, role = UserRole.STAFF)
    void STAFF는_동아리를_등록하고_로그인_ID가_서비스로_전달된다() throws Exception {
        when(clubAdminService.create(eq(7L), any(CreateClubRequest.class))).thenReturn(clubResponse());

        mockMvc.perform(post("/api/v1/admin/clubs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLUB_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("동아리를 등록했습니다."))
                .andExpect(jsonPath("$.data.createdById").value(7));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void 인스타그램_링크_형식이_아니면_400이다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/clubs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"HIPCOM\",\"intro\":\"소개\",\"instagramUrl\":\"@hipcom_yu\",\"sortOrder\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockLoginUser(role = UserRole.OWNER)
    void OWNER는_동아리를_수정한다() throws Exception {
        when(clubAdminService.update(eq(3L), any(UpdateClubRequest.class))).thenReturn(clubResponse());

        mockMvc.perform(patch("/api/v1/admin/clubs/3")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLUB_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("동아리를 수정했습니다."));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_multipart로_대표_사진을_올린다() throws Exception {
        when(clubAdminService.uploadPhoto(eq(3L), any())).thenReturn(clubResponse());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/clubs/3/photo")
                        .file(new MockMultipartFile("file", "hipcom.png", "image/png", new byte[] {1, 2, 3}))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대표 사진을 올렸습니다."))
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_대표_사진을_지우면_204다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/clubs/3/photo").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_동아리를_삭제하면_204다() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/clubs/3").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void USER는_라인업_운영_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/clubs")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CLUB_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static AdminClubResponse clubResponse() {
        return AdminClubResponse.builder()
                .id(3L).name("HIPCOM").intro("영남대학교 유일 힙합 동아리 HIPCOM").genre("힙합")
                .signatureSong("최준현-거북당").instagramUrl("https://www.instagram.com/hipcom_yu")
                .sortOrder(3).createdById(7L)
                .build();
    }
}
