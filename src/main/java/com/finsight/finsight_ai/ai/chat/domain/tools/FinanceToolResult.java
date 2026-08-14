package com.finsight.finsight_ai.ai.chat.domain.tools;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record FinanceToolResult(
        Object data,
        List<NumericEvidence> numericEvidence,
        Set<UUID> transactionEvidenceIds
) {
    public FinanceToolResult {
        Objects.requireNonNull(data, "Tool result data is required");
        numericEvidence = numericEvidence == null ? List.of() : List.copyOf(numericEvidence);
        transactionEvidenceIds = transactionEvidenceIds == null
                ? Set.of()
                : Set.copyOf(transactionEvidenceIds);
    }

    public static FinanceToolResult of(Object data, List<NumericEvidence> numericEvidence) {
        return new FinanceToolResult(data, numericEvidence, Set.of());
    }

    public static FinanceToolResult of(Object data, List<NumericEvidence> numericEvidence, Set<UUID> transactionEvidenceIds) {
        return new FinanceToolResult(data, numericEvidence, transactionEvidenceIds);
    }
}
