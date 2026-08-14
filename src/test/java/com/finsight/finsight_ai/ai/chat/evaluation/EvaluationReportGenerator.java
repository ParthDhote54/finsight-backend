package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class EvaluationReportGenerator {

    private final ObjectMapper objectMapper;
    private final EvaluationMetricCalculator metricCalculator;

    public EvaluationReportGenerator(ObjectMapper objectMapper, EvaluationMetricCalculator metricCalculator) {
        this.objectMapper = objectMapper;
        this.metricCalculator = metricCalculator;
    }

    public EvaluationReportArtifact writeReport(
            String evaluationRunId,
            List<EvaluationRunResult> results,
            Path outputDirectory
    ) throws IOException {
        Files.createDirectories(outputDirectory);
        EvaluationMetrics metrics = metricCalculator.calculate(results);
        Path jsonPath = outputDirectory.resolve(evaluationRunId + ".json");
        Path markdownPath = outputDirectory.resolve(evaluationRunId + ".md");

        Map<String, Object> payload = Map.of(
                "evaluationRunId", evaluationRunId,
                "metrics", metrics,
                "results", results.stream().map(this::safeResult).toList());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), payload);
        Files.writeString(markdownPath, markdown(evaluationRunId, results, metrics));
        return new EvaluationReportArtifact(jsonPath, markdownPath, metrics);
    }

    private Map<String, Object> safeResult(EvaluationRunResult result) {
        return Map.ofEntries(
                Map.entry("caseId", result.caseId()),
                Map.entry("attemptNumber", result.attemptNumber()),
                Map.entry("conversationId", String.valueOf(result.conversationId())),
                Map.entry("requestCorrelationId", result.requestCorrelationId() == null
                        ? "UNAVAILABLE"
                        : result.requestCorrelationId()),
                Map.entry("systemOutcomePassed", result.systemOutcomePassed()),
                Map.entry("modelSelectionPassed", result.modelSelectionPassed()),
                Map.entry("modelToolCalls", result.modelToolCalls().stream().map(ToolInvocationTrace::toolName).toList()),
                Map.entry("recoveryToolCalls", result.recoveryToolCalls().stream().map(ToolInvocationTrace::toolName).toList()),
                Map.entry("recoveryTriggered", result.recoveryTriggered()),
                Map.entry("numericValidation", result.numericValidation()),
                Map.entry("citationValidation", result.citationValidation()),
                Map.entry("semanticValidation", result.semanticValidation()),
                Map.entry("failureReasons", result.failureReasons()),
                Map.entry("latencyMs", result.totalLatencyMs() == null ? "UNAVAILABLE" : result.totalLatencyMs()));
    }

    private String markdown(String evaluationRunId, List<EvaluationRunResult> results, EvaluationMetrics metrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("# FinSight Evaluation Report\n\n");
        builder.append("Run: `").append(evaluationRunId).append("`\n\n");
        builder.append("## Summary\n\n");
        builder.append("- Cases executed: ").append(metrics.casesExecuted()).append('\n');
        builder.append("- System success: ").append(metrics.systemSuccessCount()).append('/').append(metrics.casesExecuted()).append('\n');
        builder.append("- Model tool selection: ").append(metrics.modelToolSelectionSuccessCount())
                .append('/').append(metrics.modelToolSelectionEligibleCases()).append('\n');
        builder.append("- Recovery triggered: ").append(metrics.recoveryTriggeredCount()).append('\n');
        builder.append("- Unsupported number escapes: ").append(metrics.unsupportedNumberEscapeCount()).append("\n\n");
        builder.append("| Case | System | Model Tool Selection | Recovery | Validators | Failure |\n");
        builder.append("| ---- | ------ | -------------------- | -------- | ---------- | ------- |\n");
        for (EvaluationRunResult result : results) {
            builder.append("| ")
                    .append(result.caseId())
                    .append(" | ")
                    .append(result.systemOutcomePassed() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(result.modelSelectionPassed() ? "PASS" : "FAIL")
                    .append(" | ")
                    .append(result.recoveryTriggered() ? "YES" : "NO")
                    .append(" | ")
                    .append(result.numericValidation()).append('/')
                    .append(result.citationValidation()).append('/')
                    .append(result.semanticValidation())
                    .append(" | ")
                    .append(result.failureReasons().isEmpty() ? "NONE" : result.failureReasons())
                    .append(" |\n");
        }
        return builder.toString();
    }
}
