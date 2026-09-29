package com.yufesta.domain.moderation.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.CaseResult;
import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.Expected;
import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.Outcome;
import com.yufesta.domain.moderation.evaluation.ModerationEvaluationRunner.Split;
import com.yufesta.domain.moderation.evaluation.ModerationEvaluationReport.Metrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationEvaluationRunnerTest {

    @Test
    void 차단율과_오탐률과_SKIPPED_비율을_계산한다() {
        List<CaseResult> results = List.of(
                result("B1", Expected.BLOCK, Outcome.BLOCK),
                result("B2", Expected.BLOCK, Outcome.ALLOW),
                result("B3", Expected.BLOCK, Outcome.SKIPPED),
                result("A1", Expected.ALLOW, Outcome.ALLOW),
                result("A2", Expected.ALLOW, Outcome.BLOCK)
        );

        Metrics metrics = ModerationEvaluationReport.summarize(results);

        assertThat(metrics)
                .extracting(
                        Metrics::total,
                        Metrics::truePositive,
                        Metrics::falseNegative,
                        Metrics::falsePositive,
                        Metrics::trueNegative,
                        Metrics::skipped
                )
                .containsExactly(5L, 1L, 2L, 1L, 1L, 1L);
        assertThat(metrics.harmfulRecall()).isEqualTo(1.0 / 3.0);
        assertThat(metrics.falsePositiveRate()).isEqualTo(0.5);
        assertThat(metrics.skippedRate()).isEqualTo(0.2);
    }

    private static CaseResult result(String id, Expected expected, Outcome actual) {
        return new CaseResult(id, "category", Split.TUNE, expected, actual, 10L);
    }
}
