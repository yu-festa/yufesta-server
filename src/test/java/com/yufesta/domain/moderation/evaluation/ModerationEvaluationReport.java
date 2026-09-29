package com.yufesta.domain.moderation.evaluation;

import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.CaseResult;
import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.Expected;
import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.Outcome;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 원문을 포함하지 않는 콘텐츠 필터 집계 지표와 평가 보고서를 생성한다. */
final class ModerationEvaluationReport {

    private ModerationEvaluationReport() {
    }

    static Report create(Path datasetPath, String model, BigDecimal threshold, List<CaseResult> results) {
        Map<String, Metrics> categories = new TreeMap<>();
        results.stream().map(CaseResult::category).distinct().forEach(category -> categories.put(
                category,
                summarize(results.stream().filter(result -> result.category().equals(category)).toList())
        ));
        return new Report(
                Instant.now().toString(),
                sha256(datasetPath),
                model,
                threshold.toPlainString(),
                summarize(results),
                Map.copyOf(categories),
                latency(results),
                List.copyOf(results)
        );
    }

    static Metrics summarize(List<CaseResult> results) {
        long expectedBlock = count(results, Expected.BLOCK, null);
        long expectedAllow = count(results, Expected.ALLOW, null);
        long truePositive = count(results, Expected.BLOCK, Outcome.BLOCK);
        long falseNegative = expectedBlock - truePositive;
        long falsePositive = count(results, Expected.ALLOW, Outcome.BLOCK);
        long trueNegative = expectedAllow - falsePositive;
        long skipped = results.stream().filter(result -> result.actual() == Outcome.SKIPPED).count();
        return new Metrics(
                results.size(),
                expectedBlock,
                expectedAllow,
                truePositive,
                falseNegative,
                falsePositive,
                trueNegative,
                skipped,
                ratio(truePositive, expectedBlock),
                ratio(falsePositive, expectedAllow),
                ratio(skipped, results.size())
        );
    }

    private static long count(List<CaseResult> results, Expected expected, Outcome actual) {
        return results.stream()
                .filter(result -> result.expected() == expected)
                .filter(result -> actual == null || result.actual() == actual)
                .count();
    }

    private static Latency latency(List<CaseResult> results) {
        List<Long> sorted = results.stream().map(CaseResult::latencyMillis).sorted().toList();
        long total = sorted.stream().mapToLong(Long::longValue).sum();
        return new Latency(
                total / sorted.size(),
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                sorted.get(sorted.size() - 1)
        );
    }

    private static long percentile(List<Long> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(index);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private static String sha256(Path path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("cannot hash dataset", exception);
        }
    }

    record Metrics(
            long total,
            long expectedBlock,
            long expectedAllow,
            long truePositive,
            long falseNegative,
            long falsePositive,
            long trueNegative,
            long skipped,
            double harmfulRecall,
            double falsePositiveRate,
            double skippedRate
    ) {
    }

    record Latency(long averageMillis, long p50Millis, long p95Millis, long maxMillis) {
    }

    record Report(
            String generatedAt,
            String datasetSha256,
            String model,
            String threshold,
            Metrics overall,
            Map<String, Metrics> byCategory,
            Latency latency,
            List<CaseResult> cases
    ) {
    }
}
