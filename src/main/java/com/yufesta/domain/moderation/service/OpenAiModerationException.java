package com.yufesta.domain.moderation.service;

/** OpenAI 모더레이션 API를 호출하거나 응답을 해석하지 못했을 때의 내부 예외. */
public class OpenAiModerationException extends RuntimeException {

    public OpenAiModerationException(String message) {
        super(message);
    }

    public OpenAiModerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
