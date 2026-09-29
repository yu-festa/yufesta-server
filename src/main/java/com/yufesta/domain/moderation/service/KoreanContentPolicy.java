package com.yufesta.domain.moderation.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 정규화된 콘텐츠에 명확한 한국어 욕설·위협·집단 비하 규칙을 적용한다. */
@Component
public class KoreanContentPolicy {

    private static final List<String> PROFANITY_TERMS = List.of(
            "\uC2DC\uBC1C",
            "\uC528\uBC1C",
            "\uC528\uC774\uBC1C",
            "\uAC1C\uC0C8\uB07C",
            "\uBCD1\uC2E0",
            "\u3145\u3142",
            "\u3146\u3142"
    );
    private static final List<String> ALLOWED_TERMS = List.of(
            "\uC2DC\uBC1C\uC810"
    );
    private static final List<String> THREAT_TERMS = List.of(
            "\uC8FD\uC774\uACA0\uB2E4",
            "\uC8FD\uC5EC\uBC84\uB9B0\uB2E4",
            "\uAC00\uB9CC\uB450\uC9C0\uC54A\uACA0\uB2E4",
            "\uCC3E\uC544\uAC00\uC11C\uB54C\uB9B4\uAC70\uC57C",
            "\uC5C6\uC560\uBC84\uB9AC\uACA0\uB2E4",
            "\uD3ED\uB825\uC744\uD589\uC0AC\uD558\uACA0\uB2E4",
            "\uBD88\uD0DC\uC6B0\uACA0\uB2E4"
    );
    private static final List<String> GROUP_TERMS = List.of(
            "\uC870\uC120\uC778",
            "\uC678\uAD6D\uC778",
            "\uC774\uC8FC\uBBFC",
            "\uC7A5\uC560\uC778",
            "\uB178\uC778",
            "\uC5EC\uC790",
            "\uB0A8\uC790",
            "\uD2B9\uC815\uC9C0\uC5ED\uC0AC\uB78C"
    );
    private static final List<String> DEGRADING_TERMS = List.of(
            "\uBBF8\uAC1C",
            "\uC5F4\uB4F1",
            "\uC0AC\uB77C\uC838\uC57C",
            "\uC218\uC900\uC774\uB0AE",
            "\uBBFC\uD3D0",
            "\uC4F8\uBAA8\uC5C6",
            "\uB0B4\uCAD3\uC544\uC57C",
            "\uBA78\uC885"
    );

    private final KoreanContentNormalizer normalizer;
    private final List<String> profanityTerms;
    private final List<String> allowedTerms;
    private final List<String> threatTerms;
    private final List<String> groupTerms;
    private final List<String> degradingTerms;

    public KoreanContentPolicy(KoreanContentNormalizer normalizer) {
        this.normalizer = normalizer;
        this.profanityTerms = normalize(PROFANITY_TERMS);
        this.allowedTerms = normalize(ALLOWED_TERMS);
        this.threatTerms = normalize(THREAT_TERMS);
        this.groupTerms = normalize(GROUP_TERMS);
        this.degradingTerms = normalize(DEGRADING_TERMS);
    }

    public Optional<LocalModerationViolation> findViolation(String content, List<String> configuredBannedWords) {
        String normalized = normalizer.normalize(content);
        String comparisonTarget = removeAllowedTerms(normalized);

        if (containsAny(comparisonTarget, normalize(configuredBannedWords))) {
            return Optional.of(LocalModerationViolation.BANNED_WORD);
        }
        if (containsAny(comparisonTarget, profanityTerms)) {
            return Optional.of(LocalModerationViolation.PROFANITY);
        }
        if (containsAny(normalized, threatTerms)) {
            return Optional.of(LocalModerationViolation.THREAT);
        }
        if (containsAny(normalized, groupTerms) && containsAny(normalized, degradingTerms)) {
            return Optional.of(LocalModerationViolation.HATE);
        }
        return Optional.empty();
    }

    private String removeAllowedTerms(String content) {
        String result = content;
        for (String allowedTerm : allowedTerms) {
            result = result.replace(allowedTerm, "");
        }
        return result;
    }

    private List<String> normalize(List<String> terms) {
        return terms.stream()
                .map(normalizer::normalize)
                .filter(term -> !term.isBlank())
                .toList();
    }

    private static boolean containsAny(String content, List<String> terms) {
        return terms.stream().anyMatch(content::contains);
    }
}
