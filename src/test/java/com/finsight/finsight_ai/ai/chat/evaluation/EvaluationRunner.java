package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.application.IntentClassifier;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.ports.in.ChatUseCase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class EvaluationRunner {

    private final ChatUseCase chatUseCase;
    private final EvaluationAuditSource auditSource;
    private final IntentClassifier intentClassifier;
    private final ChatAuditTraceExtractor traceExtractor;
    private final EvaluationScorer scorer;
    private final Supplier<String> modelIdSupplier;
    private final String promptVersion;

    public EvaluationRunner(
            ChatUseCase chatUseCase,
            EvaluationAuditSource auditSource,
            IntentClassifier intentClassifier,
            ObjectMapper objectMapper,
            EvaluationScorer scorer,
            Supplier<String> modelIdSupplier,
            String promptVersion
    ) {
        this.chatUseCase = chatUseCase;
        this.auditSource = auditSource;
        this.intentClassifier = intentClassifier;
        this.traceExtractor = new ChatAuditTraceExtractor(objectMapper);
        this.scorer = scorer;
        this.modelIdSupplier = modelIdSupplier == null ? () -> null : modelIdSupplier;
        this.promptVersion = promptVersion;
    }

    public List<EvaluationRunResult> run(String evaluationRunId, UUID userId, List<EvaluationCase> cases) {
        List<EvaluationRunResult> results = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            UUID conversationId = null;
            int turnNumber = 0;
            for (EvaluationTurn turn : evaluationCase.turns()) {
                turnNumber++;
                long started = System.currentTimeMillis();
                ChatResponse response = null;
                EvaluationExecutionTrace trace;
                Set<UUID> beforeAuditIds = auditIds(auditSource.auditsForConversation(userId, conversationId));
                try {
                    response = chatUseCase.processChat(userId, new ChatRequest(turn.question(), conversationId));
                    long latency = System.currentTimeMillis() - started;
                    conversationId = response.conversationId();
                    AuditCorrelation correlation = correlateAudit(
                            beforeAuditIds,
                            auditSource.auditsForConversation(userId, conversationId),
                            turn.question());
                    if (correlation.audit() != null) {
                        trace = traceExtractor.fromAudit(
                                correlation.audit(),
                                response,
                                intentClassifier.classify(turn.question()),
                                modelIdSupplier.get(),
                                promptVersion,
                                latency);
                    } else {
                        trace = traceExtractor.withoutAudit(
                                conversationId,
                                response,
                                intentClassifier.classify(turn.question()),
                                modelIdSupplier.get(),
                                promptVersion,
                                latency,
                                correlation.failureReason().name());
                    }
                } catch (RuntimeException exception) {
                    long latency = System.currentTimeMillis() - started;
                    trace = traceExtractor.withoutAudit(
                            conversationId,
                            response,
                            intentClassifier.classify(turn.question()),
                            modelIdSupplier.get(),
                            promptVersion,
                            latency,
                            exception.getClass().getSimpleName());
                }
                results.add(scorer.score(
                        evaluationRunId,
                        evaluationCase,
                        turnNumber,
                        trace));
            }
        }
        return List.copyOf(results);
    }

    private Set<UUID> auditIds(List<ChatAuditLogEntity> audits) {
        Set<UUID> ids = new HashSet<>();
        for (ChatAuditLogEntity audit : audits == null ? List.<ChatAuditLogEntity>of() : audits) {
            if (audit.getId() != null) {
                ids.add(audit.getId());
            }
        }
        return ids;
    }

    private AuditCorrelation correlateAudit(
            Set<UUID> beforeAuditIds,
            List<ChatAuditLogEntity> afterAudits,
            String userMessage
    ) {
        List<ChatAuditLogEntity> created = (afterAudits == null ? List.<ChatAuditLogEntity>of() : afterAudits).stream()
                .filter(audit -> audit.getId() != null)
                .filter(audit -> !beforeAuditIds.contains(audit.getId()))
                .sorted(Comparator.comparing(ChatAuditLogEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (created.isEmpty()) {
            return AuditCorrelation.failure(EvaluationFailureReason.AUDIT_NOT_FOUND);
        }
        List<ChatAuditLogEntity> plausible = created.stream()
                .filter(audit -> userMessage == null || userMessage.equals(audit.getUserMessage()))
                .toList();
        if (plausible.isEmpty()) {
            return AuditCorrelation.failure(EvaluationFailureReason.AUDIT_NOT_FOUND);
        }
        if (plausible.size() > 1) {
            return AuditCorrelation.failure(EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS);
        }
        return new AuditCorrelation(plausible.get(0), null);
    }

    private record AuditCorrelation(
            ChatAuditLogEntity audit,
            EvaluationFailureReason failureReason
    ) {
        private static AuditCorrelation failure(EvaluationFailureReason reason) {
            return new AuditCorrelation(null, reason);
        }
    }
}
