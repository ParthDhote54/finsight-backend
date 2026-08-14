package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReportGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportArtifactsAreJsonReadableMarkdownUsefulAndDoNotExposeSecrets(@TempDir Path tempDir) throws Exception {
        EvaluationRunResult result = new EvaluationRunResult(
                "eval-report",
                "case-1",
                1,
                UUID.randomUUID(),
                "audit-1",
                "synthetic-model",
                null,
                null,
                List.of(),
                List.of(ToolInvocationTrace.recovery("balance_reconciler", Map.of())),
                false,
                EvaluationValidationStatus.PASSED,
                EvaluationValidationStatus.PASSED,
                EvaluationValidationStatus.PASSED,
                false,
                true,
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                Set.of(EvaluationFailureReason.MODEL_TOOL_MISS_RECOVERED),
                25L,
                new EvaluationTokenUsage(10, 5, 15));
        EvaluationRunResult correlationFailure = new EvaluationRunResult(
                "eval-report",
                "case-correlation-failure",
                1,
                UUID.randomUUID(),
                null,
                "synthetic-model",
                null,
                null,
                List.of(),
                List.of(),
                false,
                EvaluationValidationStatus.NOT_RUN,
                EvaluationValidationStatus.NOT_RUN,
                EvaluationValidationStatus.NOT_RUN,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                Set.of(EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS),
                25L,
                null);

        EvaluationReportGenerator generator = new EvaluationReportGenerator(
                objectMapper,
                new EvaluationMetricCalculator());
        EvaluationReportArtifact artifact = generator.writeReport("eval-report", List.of(result, correlationFailure), tempDir);
        EvaluationReportArtifact persistentArtifact = generator.writeReport(
                "phase5b-synthetic-sample",
                List.of(result, correlationFailure),
                Paths.get("target", "phase5b-evaluation"));

        JsonNode json = objectMapper.readTree(artifact.jsonPath().toFile());
        String markdown = Files.readString(artifact.markdownPath());
        JsonNode persistentJson = objectMapper.readTree(persistentArtifact.jsonPath().toFile());

        assertThat(json.path("evaluationRunId").asText()).isEqualTo("eval-report");
        assertThat(json.path("results").get(0).path("modelToolCalls")).isEmpty();
        assertThat(json.path("results").get(0).path("recoveryToolCalls").get(0).asText())
                .isEqualTo("balance_reconciler");
        assertThat(markdown)
                .contains("Model Tool Selection")
                .contains("MODEL_TOOL_MISS_RECOVERED")
                .contains("AUDIT_CORRELATION_AMBIGUOUS")
                .contains("Recovery");
        assertThat(markdown)
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ")
                .doesNotContain("ADC")
                .doesNotContain("private_key")
                .doesNotContain("DB_PASSWORD");
        assertThat(persistentJson.path("evaluationRunId").asText()).isEqualTo("phase5b-synthetic-sample");
        assertThat(Files.exists(persistentArtifact.markdownPath())).isTrue();
    }
}
