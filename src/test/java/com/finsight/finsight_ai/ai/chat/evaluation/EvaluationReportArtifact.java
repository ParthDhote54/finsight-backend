package com.finsight.finsight_ai.ai.chat.evaluation;

import java.nio.file.Path;

public record EvaluationReportArtifact(
        Path jsonPath,
        Path markdownPath,
        EvaluationMetrics metrics
) {
}
