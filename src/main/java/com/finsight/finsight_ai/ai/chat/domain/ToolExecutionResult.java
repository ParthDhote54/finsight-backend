package com.finsight.finsight_ai.ai.chat.domain;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ToolExecutionResult(
        Status status,
        String responseJson,
        String errorCode,
        List<NumericEvidence> numericEvidence,
        Set<UUID> transactionEvidenceIds
) {
    public ToolExecutionResult {
        Objects.requireNonNull(status, "Tool execution status is required");
        Objects.requireNonNull(responseJson, "Tool response JSON is required");
        numericEvidence = numericEvidence == null ? List.of() : List.copyOf(numericEvidence);
        transactionEvidenceIds = transactionEvidenceIds == null
                ? Set.of()
                : Set.copyOf(transactionEvidenceIds);
    }

    public static ToolExecutionResult success(String responseJson) {
        return new ToolExecutionResult(Status.SUCCESS, responseJson, null, List.of(), Set.of());
    }

    public static ToolExecutionResult success(String responseJson,
                                              List<NumericEvidence> numericEvidence,
                                              Set<UUID> transactionEvidenceIds) {
        return new ToolExecutionResult(
                Status.SUCCESS, responseJson, null, numericEvidence, transactionEvidenceIds);
    }

    public static ToolExecutionResult modelCorrectableError(String responseJson, String errorCode) {
        return new ToolExecutionResult(
                Status.MODEL_CORRECTABLE_ERROR, responseJson, errorCode, List.of(), Set.of());
    }

    public static ToolExecutionResult systemError(String responseJson, String errorCode) {
        return new ToolExecutionResult(
                Status.SYSTEM_ERROR, responseJson, errorCode, List.of(), Set.of());
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        MODEL_CORRECTABLE_ERROR,
        SYSTEM_ERROR
    }
}
