package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.application.IntentBucket;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.domain.TokenUsageMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ChatAuditTraceExtractor {

    private final ObjectMapper objectMapper;

    public ChatAuditTraceExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvaluationExecutionTrace fromAudit(
            ChatAuditLogEntity audit,
            ChatResponse response,
            IntentBucket actualIntent,
            String modelId,
            String promptVersion,
            Long latencyMs
    ) {
        List<Map<String, Object>> rawTrace = rawTrace(audit);
        List<ToolInvocationTrace> modelCalls = new ArrayList<>();
        List<ToolInvocationTrace> recoveryCalls = new ArrayList<>();
        EvaluationValidationStatus numeric = EvaluationValidationStatus.NOT_RUN;
        EvaluationValidationStatus citation = EvaluationValidationStatus.NOT_RUN;
        EvaluationValidationStatus semantic = EvaluationValidationStatus.NOT_RUN;
        boolean correctionTriggered = false;
        boolean unsupportedNumberInSuccessfulAnswer = false;

        for (Map<String, Object> item : rawTrace) {
            String stage = text(item.get("stage"));
            if ("TOOL".equals(stage)) {
                ToolInvocationTrace invocation = toolInvocation(item);
                if (invocation.source() == ToolInvocationSource.DETERMINISTIC_RECOVERY) {
                    recoveryCalls.add(invocation);
                } else {
                    modelCalls.add(invocation);
                }
            }
            if ("VALIDATION".equals(stage)) {
                numeric = status(item.get("numericStatus"));
                citation = status(item.get("citationStatus"));
                semantic = status(item.get("semanticStatus"));
                correctionTriggered = correctionTriggered
                        || "SYSTEM_ERROR".equals(text(item.get("status")));
                unsupportedNumberInSuccessfulAnswer = unsupportedNumberInSuccessfulAnswer
                        || unsupportedClaimsPresent(item);
            }
        }

        TokenUsageMetaData meta = response == null ? null : response.metaData();
        boolean ragUsed = meta != null && meta.usedRag();
        boolean financialEvidencePresent = meta != null && meta.usedTool();
        boolean unsupportedNumberEscaped = !audit.isFlaggedHallucination() && unsupportedNumberInSuccessfulAnswer;

        return new EvaluationExecutionTrace(
                response == null ? audit.getConversationId() : response.conversationId(),
                audit.getId() == null ? null : audit.getId().toString(),
                modelId,
                promptVersion,
                actualIntent,
                modelCalls,
                recoveryCalls,
                ragUsed,
                numeric,
                citation,
                semantic,
                correctionTriggered,
                financialEvidencePresent,
                safeRefusalObserved(audit.getFinalAnswer(), financialEvidencePresent, unsupportedNumberEscaped),
                unsupportedNumberEscaped,
                latencyMs,
                tokenUsage(audit, meta),
                null);
    }

    public EvaluationExecutionTrace withoutAudit(
            UUID conversationId,
            ChatResponse response,
            IntentBucket actualIntent,
            String modelId,
            String promptVersion,
            Long latencyMs,
            String executionError
    ) {
        TokenUsageMetaData meta = response == null ? null : response.metaData();
        return new EvaluationExecutionTrace(
                conversationId,
                null,
                modelId,
                promptVersion,
                actualIntent,
                List.of(),
                List.of(),
                meta != null && meta.usedRag(),
                EvaluationValidationStatus.NOT_RUN,
                EvaluationValidationStatus.NOT_RUN,
                EvaluationValidationStatus.NOT_RUN,
                false,
                meta != null && meta.usedTool(),
                safeRefusalObserved(response == null ? null : response.answer(), meta != null && meta.usedTool(), false),
                false,
                latencyMs,
                tokenUsage(null, meta),
                executionError);
    }

    private ToolInvocationTrace toolInvocation(Map<String, Object> item) {
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = item.get("arguments") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        EvaluationToolStatus status = toolStatus(item.get("status"));
        boolean validArguments = status != EvaluationToolStatus.MODEL_CORRECTABLE_ERROR
                && !"INVALID_ARGUMENT_TYPE".equals(text(item.get("errorCode")))
                && !"INVALID_ARGUMENT_VALUE".equals(text(item.get("errorCode")));
        ToolInvocationSource source = "DETERMINISTIC_RECOVERY".equals(text(item.get("invocationSource")))
                ? ToolInvocationSource.DETERMINISTIC_RECOVERY
                : ToolInvocationSource.MODEL;
        return new ToolInvocationTrace(
                text(item.get("toolName")),
                source,
                arguments,
                validArguments,
                status,
                text(item.get("errorCode")));
    }

    private List<Map<String, Object>> rawTrace(ChatAuditLogEntity audit) {
        if (audit == null || audit.getToolCalls() == null || audit.getToolCalls().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(audit.getToolCalls(), new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of(Map.of(
                    "stage", "TRACE_PARSE",
                    "status", "SYSTEM_ERROR",
                    "errorCode", "AUDIT_TRACE_PARSE_FAILED"));
        }
    }

    private boolean unsupportedClaimsPresent(Map<String, Object> item) {
        Object claims = item.get("unsupportedNumericClaims");
        return claims instanceof List<?> list && !list.isEmpty();
    }

    private EvaluationValidationStatus status(Object value) {
        String text = text(value);
        if ("PASSED".equals(text)) {
            return EvaluationValidationStatus.PASSED;
        }
        if ("FAILED".equals(text)) {
            return EvaluationValidationStatus.FAILED;
        }
        return EvaluationValidationStatus.NOT_RUN;
    }

    private EvaluationToolStatus toolStatus(Object value) {
        String text = text(value);
        if ("MODEL_CORRECTABLE_ERROR".equals(text)) {
            return EvaluationToolStatus.MODEL_CORRECTABLE_ERROR;
        }
        if ("SYSTEM_ERROR".equals(text)) {
            return EvaluationToolStatus.SYSTEM_ERROR;
        }
        return EvaluationToolStatus.SUCCESS;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private EvaluationTokenUsage tokenUsage(ChatAuditLogEntity audit, TokenUsageMetaData meta) {
        if (audit != null) {
            return new EvaluationTokenUsage(
                    audit.getPromptTokens(),
                    audit.getCompletionTokens(),
                    audit.getTotalTokens());
        }
        if (meta != null) {
            return new EvaluationTokenUsage(
                    meta.promptTokens(),
                    meta.completionTokens(),
                    meta.totalTokens());
        }
        return null;
    }

    private boolean safeRefusalObserved(String answer, boolean financialEvidencePresent, boolean unsupportedNumberEscaped) {
        if (answer == null || unsupportedNumberEscaped) {
            return false;
        }
        String normalized = answer.toLowerCase(Locale.ROOT);
        boolean boundaryLanguage = normalized.contains("cannot provide")
                || normalized.contains("can't provide")
                || normalized.contains("couldn't verify")
                || normalized.contains("cannot verify")
                || normalized.contains("consult a qualified")
                || normalized.contains("limited to analyzing")
                || normalized.contains("not provide specific investment advice");
        return boundaryLanguage && !financialEvidencePresent;
    }
}
