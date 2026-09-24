package com.yufesta.domain.cheer.service;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.common.security.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 응원 메시지 작성자를 브라우저 단위로 구분하는 익명 키 쿠키를 관리 */
@Service
public class AnonymousKeyService {

    public static final String COOKIE_NAME = "anon_key";

    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final AuthProperties.Cookie cookieProperties;

    public AnonymousKeyService(AuthProperties authProperties) {
        this.cookieProperties = authProperties.cookie();
    }

    /**
     * 요청의 익명 키를 반환하고, 없으면 새 키를 HttpOnly 쿠키로 발급한다(FR-CH-02).
     */
    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        return extract(request).orElseGet(() -> issue(response));
    }

    /** 요청에 포함된 익명 키를 조회한다. 없으면 쿠키를 발급하지 않는다. */
    public Optional<String> extract(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    /** 익명 키 원문을 DB 저장용 SHA-256 16진수 해시로 변환한다. */
    public String hash(String anonymousKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(anonymousKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String issue(HttpServletResponse response) {
        String anonymousKey = UUID.randomUUID().toString();
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(COOKIE_NAME, anonymousKey)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE);
        if (StringUtils.hasText(cookieProperties.domain())) {
            cookie.domain(cookieProperties.domain());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
        return anonymousKey;
    }
}
