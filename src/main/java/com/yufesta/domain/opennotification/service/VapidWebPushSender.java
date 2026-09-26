package com.yufesta.domain.opennotification.service;

import com.yufesta.domain.opennotification.OpenNotificationProperties;
import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 환경변수의 VAPID 키로 Web Push payload를 암호화·전송한다. */
@Component
public class VapidWebPushSender implements WebPushSender {

    private final OpenNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public VapidWebPushSender(OpenNotificationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WebPushDeliveryResult send(OpenNotificationDispatch dispatch, OpenNotificationPayload payload) {
        try {
            registerBouncyCastleProvider();
            PushService pushService = new PushService(
                    properties.vapidPublicKey(),
                    properties.vapidPrivateKey(),
                    properties.vapidSubject()
            );
            Notification notification = new Notification(
                    dispatch.endpoint(),
                    dispatch.p256dhKey(),
                    dispatch.authKey(),
                    objectMapper.writeValueAsString(payload)
            );
            HttpResponse response = pushService.send(notification);
            return new WebPushDeliveryResult(response.getStatusLine().getStatusCode());
        } catch (Exception exception) {
            throw new WebPushDeliveryException(exception);
        }
    }

    private static void registerBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
