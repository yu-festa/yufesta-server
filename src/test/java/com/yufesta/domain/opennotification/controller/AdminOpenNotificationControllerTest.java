package com.yufesta.domain.opennotification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationTestResponse;
import com.yufesta.domain.opennotification.service.OpenNotificationTestService;
import com.yufesta.domain.user.enums.UserRole;
import com.yufesta.support.ControllerTestSupport;
import com.yufesta.support.WithMockLoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = AdminOpenNotificationController.class)
class AdminOpenNotificationControllerTest extends ControllerTestSupport {

    private static final String TEST_PUSH_PATH = "/api/v1/admin/open-notifications/test-push";

    @MockitoBean
    private OpenNotificationTestService openNotificationTestService;

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void STAFF는_CSRF와_함께_즉시_테스트_푸시를_발송한다() throws Exception {
        when(openNotificationTestService.sendTest(any(CreateOpenNotificationSubscriptionRequest.class)))
                .thenReturn(OpenNotificationTestResponse.builder().accepted(true).statusCode(201).build());

        mockMvc.perform(post(TEST_PUSH_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.statusCode").value(201));
    }

    @Test
    @WithMockLoginUser(role = UserRole.USER)
    void 일반_회원은_테스트_푸시를_발송할_수_없다() throws Exception {
        mockMvc.perform(post(TEST_PUSH_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(openNotificationTestService, never()).sendTest(any());
    }

    @Test
    void 비로그인은_테스트_푸시를_발송할_수_없다() throws Exception {
        mockMvc.perform(post(TEST_PUSH_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockLoginUser(role = UserRole.STAFF)
    void CSRF_토큰_없이_테스트_푸시를_발송하면_403이다() throws Exception {
        mockMvc.perform(post(TEST_PUSH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscriptionBody()))
                .andExpect(status().isForbidden());

        verify(openNotificationTestService, never()).sendTest(any());
    }

    private static String subscriptionBody() {
        return "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/example\","
                + "\"keys\":{\"p256dh\":\"p256dh-key\",\"auth\":\"auth-key\"}}";
    }
}
