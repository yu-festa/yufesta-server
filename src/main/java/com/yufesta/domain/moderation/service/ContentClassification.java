package com.yufesta.domain.moderation.service;

import java.math.BigDecimal;
import java.util.Objects;

/** 서비스 운영 정책에 따른 소형 LLM의 콘텐츠 판정 결과. */
public record ContentClassification(Decision decision, Category category, BigDecimal confidence) {

    public ContentClassification {
        Objects.requireNonNull(decision);
        Objects.requireNonNull(category);
        Objects.requireNonNull(confidence);
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if ((decision == Decision.ALLOW && category != Category.NONE)
                || (decision == Decision.BLOCK && category == Category.NONE)) {
            throw new IllegalArgumentException("decision and category must be consistent");
        }
    }

    public boolean blocksAt(BigDecimal threshold) {
        return decision == Decision.BLOCK && confidence.compareTo(threshold) >= 0;
    }

    public enum Decision {
        ALLOW,
        BLOCK
    }

    public enum Category {
        NONE,
        PROFANITY,
        HARASSMENT,
        HATE,
        THREAT,
        SEXUAL,
        SELF_HARM,
        PRIVACY_ATTACK,
        OTHER
    }
}
