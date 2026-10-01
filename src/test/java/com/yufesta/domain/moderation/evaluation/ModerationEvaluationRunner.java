package com.yufesta.domain.moderation.evaluation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import com.yufesta.domain.appsetting.enums.SettingKey;
import com.yufesta.domain.appsetting.service.AppSettingReader;
import com.yufesta.domain.cheer.enums.ModerationStatus;
import com.yufesta.domain.moderation.config.OpenAiContentProperties;
import com.yufesta.domain.moderation.service.ContentModerationService;
import com.yufesta.domain.moderation.service.KoreanContentNormalizer;
import com.yufesta.domain.moderation.service.KoreanContentPolicy;
import com.yufesta.domain.moderation.service.OpenAiContentClassifierClient;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

/** 비공개 정답 데이터셋을 운영 콘텐츠 필터로 평가하며 원문은 저장하지 않는다. */
public final class ModerationEvaluationRunner {

    private static final BigDecimal THRESHOLD = new BigDecimal("0.5");
    private static final String MODEL = System.getenv().getOrDefault("OPENAI_CONTENT_MODEL", "gpt-4.1-mini");

    private ModerationEvaluationRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("dataset path and report path are required");
        }

        Path datasetPath = Path.of(args[0]);
        Path reportPath = Path.of(args[1]);
        ObjectMapper objectMapper = new ObjectMapper();
        List<EvaluationCase> cases = readCases(datasetPath, objectMapper);
        ContentModerationService service = moderationService(objectMapper);

        List<CaseResult> results = new ArrayList<>(cases.size());
        for (EvaluationCase evaluationCase : cases) {
            long startedAt = System.nanoTime();
            Outcome actual = evaluate(service, evaluationCase.content());
            long latencyMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            results.add(new CaseResult(
                    evaluationCase.id(),
                    evaluationCase.category(),
                    evaluationCase.split(),
                    evaluationCase.expected(),
                    actual,
                    latencyMillis
            ));
        }

        ModerationEvaluationReport.Report report = ModerationEvaluationReport.create(
                datasetPath,
                MODEL,
                THRESHOLD,
                results
        );
        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
        System.out.printf(
                "moderation evaluation: total=%d recall=%.4f falsePositiveRate=%.4f skippedRate=%.4f p95=%dms report=%s%n",
                report.overall().total(),
                report.overall().harmfulRecall(),
                report.overall().falsePositiveRate(),
                report.overall().skippedRate(),
                report.latency().p95Millis(),
                reportPath
        );
    }

    private static List<EvaluationCase> readCases(Path datasetPath, ObjectMapper objectMapper) throws IOException {
        List<EvaluationCase> cases = new ArrayList<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(datasetPath, StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                cases.add(objectMapper.readValue(line, EvaluationCase.class));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("invalid dataset line " + lineNumber, exception);
            }
        }
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("dataset must contain at least one case");
        }
        long distinctIds = cases.stream().map(EvaluationCase::id).distinct().count();
        if (distinctIds != cases.size()) {
            throw new IllegalArgumentException("dataset case ids must be unique");
        }
        return List.copyOf(cases);
    }

    private static ContentModerationService moderationService(ObjectMapper objectMapper) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }
        URI baseUrl = URI.create(System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1"));
        OpenAiContentProperties properties = new OpenAiContentProperties(
                apiKey,
                baseUrl,
                Duration.ofSeconds(2),
                MODEL
        );
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        OpenAiContentClassifierClient client = new OpenAiContentClassifierClient(httpClient, objectMapper, properties);

        AppSettingReader settings = mock(AppSettingReader.class);
        when(settings.getList(SettingKey.FILTER_BANNED_WORDS)).thenReturn(List.of());
        when(settings.getBoolean(SettingKey.FILTER_LLM_ENABLED)).thenReturn(true);
        when(settings.getDecimal(SettingKey.FILTER_LLM_THRESHOLD)).thenReturn(THRESHOLD);
        return new ContentModerationService(
                settings,
                client,
                new KoreanContentPolicy(new KoreanContentNormalizer())
        );
    }

    private static Outcome evaluate(ContentModerationService service, String content) {
        try {
            service.validateLocal(content);
            ModerationStatus status = service.moderateByLlm(content);
            return switch (status) {
                case PASSED -> Outcome.ALLOW;
                case SKIPPED -> Outcome.SKIPPED;
            };
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.CONTENT_NOT_ALLOWED) {
                return Outcome.BLOCK;
            }
            throw exception;
        }
    }

    enum Expected {
        ALLOW,
        BLOCK
    }

    enum Outcome {
        ALLOW,
        BLOCK,
        SKIPPED
    }

    enum Split {
        TUNE,
        HOLDOUT
    }

    record EvaluationCase(String id, String category, Split split, Expected expected, String content) {
    }

    record CaseResult(
            String id,
            String category,
            Split split,
            Expected expected,
            Outcome actual,
            long latencyMillis
    ) {
    }

}
