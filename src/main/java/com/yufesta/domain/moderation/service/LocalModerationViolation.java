package com.yufesta.domain.moderation.service;

/** 명확한 로컬 차단 규칙의 분류다. 원문은 로그나 응답에 포함하지 않는다. */
public enum LocalModerationViolation {
    BANNED_WORD,
    PROFANITY,
    HATE,
    THREAT
}
