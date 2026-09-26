package com.yufesta.domain.opennotification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yufesta.domain.opennotification.dto.request.CancelOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.request.CreateOpenNotificationSubscriptionRequest;
import com.yufesta.domain.opennotification.dto.response.OpenNotificationSubscriptionResponse;
import com.yufesta.domain.opennotification.dto.response.VapidPublicKeyResponse;
import com.yufesta.domain.opennotification.service.OpenNotificationSubscriptionService;
import com.yufesta.support.ControllerTestSupport;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(controllers = OpenNotificationController.class)
class OpenNotificationControllerTest extends ControllerTestSupport {

    @MockitoBean
    private OpenNotificationSubscriptionService subscriptionService;

    @Test
    void 비로그인_사용자가_VAPID_공개키를_조회한다() throws Exception {
        when(subscriptionService.getVapidPublicKey())
                .thenReturn(VapidPublicKeyResponse.builder().publicKey("public-key").build());

        mockMvc.perform(get("/api/v1/open-notifications/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicKey").value("public-key"));
    }

    @Test
    void 비로그인_사용자가_CSRF와_함께_구독을_등록한다() throws Exception {
        when(subscriptionService.subscribe(any(CreateOpenNotificationSubscriptionRequest.class)))
                .thenReturn(subscription(false));

        mockMvc.perform(post("/api/v1/open-notifications/subscriptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/example\",\"keys\":{\"p256dh\":\"p256dh-key\",\"auth\":\"auth-key\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.subscribed").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false));
    }

    @Test
    void 동일_endpoint_구독은_200으로_응답한다() throws Exception {
        when(subscriptionService.subscribe(any(CreateOpenNotificationSubscriptionRequest.class)))
                .thenReturn(subscription(true));

        mockMvc.perform(post("/api/v1/open-notifications/subscriptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/example\",\"keys\":{\"p256dh\":\"p256dh-key\",\"auth\":\"auth-key\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.duplicate").value(true));
    }

    @Test
    void 비로그인_사용자가_CSRF와_함께_구독을_취소한다() throws Exception {
        doNothing().when(subscriptionService).cancel(any(CancelOpenNotificationSubscriptionRequest.class));

        mockMvc.perform(delete("/api/v1/open-notifications/subscriptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/example\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void CSRF_토큰_없이_구독을_등록하면_403이다() throws Exception {
        mockMvc.perform(post("/api/v1/open-notifications/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/example\",\"keys\":{\"p256dh\":\"p256dh-key\",\"auth\":\"auth-key\"}}"))
                .andExpect(status().isForbidden());
    }

    private static OpenNotificationSubscriptionResponse subscription(boolean duplicate) {
        return OpenNotificationSubscriptionResponse.builder()
                .subscribed(true)
                .duplicate(duplicate)
                .scheduledFor(OffsetDateTime.parse("2026-10-02T00:00:00+09:00"))
                .build();
    }
}
