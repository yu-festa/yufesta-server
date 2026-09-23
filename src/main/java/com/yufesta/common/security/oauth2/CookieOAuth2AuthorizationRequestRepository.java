package com.yufesta.common.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * OAuth 인가 요청(state·nonce 등)과 로그인 후 이동 경로를 서버 세션 대신 HMAC 서명한 쿠키에 보관한다.
 * ALB 뒤 태스크가 2개면 콜백이 인가를 시작한 태스크로 온다는 보장이 없어 브라우저가 상태를 들고 다녀야 한다(§7)
 */
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "oauth2_auth_request";
    static final Duration MAX_AGE = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final SecretKeySpec signingKey;
    private final boolean secure;

    public CookieOAuth2AuthorizationRequestRepository(ObjectMapper objectMapper, String secret, boolean secure) {
        this.objectMapper = objectMapper;
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.secure = secure;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie == null ? null : verifyAndDeserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            expire(response);
            return;
        }
        byte[] payload = objectMapper.writeValueAsBytes(StoredRequest.from(authorizationRequest));
        String value = ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(sign(payload));
        addCookie(response, value, MAX_AGE.toSeconds());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expire(response);
        if (authorizationRequest != null) {
            // 로그인 필터가 여기서 인가 요청을 소비하므로, 같은 콜백 요청 안에서 성공 핸들러가 읽도록 이동 경로를 요청 속성에 남긴다
            request.setAttribute(
                    OAuth2RedirectRequestResolver.REDIRECT_PATH_ATTRIBUTE,
                    authorizationRequest.getAttribute(OAuth2RedirectRequestResolver.REDIRECT_PATH_ATTRIBUTE)
            );
        }
        return authorizationRequest;
    }

    // 값 형식: base64url(JSON).base64url(HMAC). 서명이 다르면 브라우저 쪽에서 바뀐 값이므로 버린다
    private OAuth2AuthorizationRequest verifyAndDeserialize(String value) {
        try {
            int dot = value.indexOf('.');
            if (dot <= 0) {
                return null;
            }
            byte[] payload = DECODER.decode(value.substring(0, dot));
            byte[] signature = DECODER.decode(value.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(payload), signature)) {
                log.info("OAuth 인가 요청 쿠키 서명 불일치");
                return null;
            }
            return objectMapper.readValue(payload, StoredRequest.class).toAuthorizationRequest();
        } catch (RuntimeException exception) {
            log.info("OAuth 인가 요청 쿠키를 읽을 수 없음: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("OAuth 쿠키 서명 초기화 실패", exception);
        }
    }

    private void expire(HttpServletResponse response) {
        addCookie(response, "", 0);
    }

    // 인가 시작과 콜백이 모두 API 호스트로 오므로 domain 없이 호스트 전용 쿠키로 둔다
    private void addCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 쿠키에 담는 필드. state·nonce(additionalParameters·attributes)나 redirect_path(attributes)를 빠뜨리면 콜백 검증이 실패한다
     */
    public record StoredRequest(
            String authorizationUri,
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            Map<String, Object> additionalParameters,
            Map<String, Object> attributes,
            String authorizationRequestUri
    ) {

        static StoredRequest from(OAuth2AuthorizationRequest request) {
            return new StoredRequest(
                    request.getAuthorizationUri(),
                    request.getClientId(),
                    request.getRedirectUri(),
                    request.getScopes(),
                    request.getState(),
                    request.getAdditionalParameters(),
                    request.getAttributes(),
                    request.getAuthorizationRequestUri()
            );
        }

        OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(authorizationUri)
                    .clientId(clientId)
                    .redirectUri(redirectUri)
                    .scopes(scopes)
                    .state(state)
                    .additionalParameters(additionalParameters)
                    .attributes(attributes)
                    .authorizationRequestUri(authorizationRequestUri)
                    .build();
        }
    }
}
