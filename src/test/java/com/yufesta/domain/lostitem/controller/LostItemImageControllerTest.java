package com.yufesta.domain.lostitem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.lostitem.dto.response.LostItemImageResponse;
import com.yufesta.domain.lostitem.service.LostItemImageService;
import com.yufesta.domain.lostitem.service.LostItemService;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = LostItemController.class)
class LostItemImageControllerTest extends ControllerTestSupport {

    @MockitoBean
    private LostItemService lostItemService;

    @MockitoBean
    private LostItemImageService lostItemImageService;

    @Test
    @WithMockLoginUser(id = 7L)
    void 작성자는_multipart로_분실물_이미지를_등록한다() throws Exception {
        when(lostItemImageService.upload(eq(7L), eq(1L), any()))
                .thenReturn(new LostItemImageResponse(3L, "https://cdn.test/lost-items/1/wallet-1600.jpg", "https://cdn.test/lost-items/1/wallet-thumb.jpg"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/lost-items/1/images")
                        .file(new MockMultipartFile("file", "wallet.png", "image/png", new byte[] {1, 2, 3}))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("분실물 이미지를 등록했습니다."))
                .andExpect(jsonPath("$.data.id").value(3));
    }

    @Test
    void 비로그인_사용자는_분실물_이미지를_등록하면_401이다() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/lost-items/1/images")
                        .file(new MockMultipartFile("file", "wallet.png", "image/png", new byte[] {1, 2, 3}))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(id = 7L)
    void 작성자는_분실물_이미지를_삭제하면_204다() throws Exception {
        mockMvc.perform(delete("/api/v1/lost-items/1/images/3").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
