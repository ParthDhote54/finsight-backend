package com.finsight.finsight_ai.ai.chat.evaluation;

import java.util.Objects;

public record EvaluationTurn(
        String turnId,
        String question
) {
    public EvaluationTurn {
        Objects.requireNonNull(turnId, "turnId is required");
        Objects.requireNonNull(question, "question is required");
    }
}
