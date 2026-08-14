package com.finsight.finsight_ai.ai.chat.evaluation;

public record EvaluationProviderProvenance(
        boolean liveProvider,
        String provider,
        String modelId,
        String projectId,
        String location,
        String implementationClass,
        String environmentLabel
) {
}
