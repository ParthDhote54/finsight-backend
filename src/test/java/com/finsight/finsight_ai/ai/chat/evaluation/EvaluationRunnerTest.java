package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.application.IntentBucket;
import com.finsight.finsight_ai.ai.chat.application.IntentClassifier;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.domain.TokenUsageMetaData;
import com.finsight.finsight_ai.ai.chat.ports.in.ChatUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationRunnerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void repeatedIdenticalPromptInSameConversationMapsEachAttemptToItsNewAudit() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        String prompt = "What about May?";
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation, prompt,
                        "spend_by_category", "audit-identical-1"))),
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation, prompt,
                        "spend_by_category", "audit-identical-2")))));
        EvaluationCase repeatedPromptCase = new EvaluationCase(
                "identical-follow-up",
                EvaluationCategory.COMPARISON,
                List.of(
                        new EvaluationTurn("identical-follow-up-turn-1", prompt),
                        new EvaluationTurn("identical-follow-up-turn-2", prompt)),
                null,
                SetUtil.of("spend_by_category"),
                SetUtil.of("spend_by_category"),
                SetUtil.of(),
                List.of(AllowedToolPath.exact("spend_by_category")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                SetUtil.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));

        List<EvaluationRunResult> results = runner(chatUseCase, auditSource)
                .run("eval-identical", userId, List.of(repeatedPromptCase));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(EvaluationRunResult::conversationId)
                .containsExactly(conversation, conversation);
        assertThat(results).extracting(EvaluationRunResult::attemptNumber)
                .containsExactly(1, 2);
        assertThat(results.get(0).requestCorrelationId()).isEqualTo(auditUuid("audit-identical-1").toString());
        assertThat(results.get(1).requestCorrelationId()).isEqualTo(auditUuid("audit-identical-2").toString());
        assertThat(results.get(0).requestCorrelationId()).isNotEqualTo(results.get(1).requestCorrelationId());
        assertThat(results.get(0).requestCorrelationId()).isNotEqualTo(auditUuid("audit-identical-2").toString());
        assertThat(results.get(1).requestCorrelationId()).isNotEqualTo(auditUuid("audit-identical-1").toString());
    }

    @Test
    void zeroNewAuditsFailsExplicitly() {
        UUID userId = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(scriptedResponse(conversation, () -> {
        })));

        EvaluationRunResult result = runner(chatUseCase, auditSource)
                .run("eval-zero", userId, List.of(singleToolCase("zero-audit", "What about May?")))
                .get(0);

        assertThat(result.systemOutcomePassed()).isFalse();
        assertThat(result.modelSelectionPassed()).isFalse();
        assertThat(result.requestCorrelationId()).isNull();
        assertThat(result.failureReasons()).contains(EvaluationFailureReason.AUDIT_NOT_FOUND);
    }

    @Test
    void multipleNewPlausibleAuditsFailAsAmbiguousWithoutPickingLatest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        String firstPrompt = "How much did I spend on food in June 2026?";
        String repeatedPrompt = "What about May?";
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation, firstPrompt,
                        "spend_by_category", "audit-before-a"))),
                scriptedResponse(conversation, () -> {
                    auditSource.add(audit(userId, conversation, repeatedPrompt,
                            "spend_by_category", "audit-ambiguous-b"));
                    auditSource.add(audit(userId, conversation, repeatedPrompt,
                            "spend_by_category", "audit-ambiguous-c"));
                })));
        EvaluationCase twoTurnCase = new EvaluationCase(
                "ambiguous-audit",
                EvaluationCategory.COMPARISON,
                List.of(
                        new EvaluationTurn("ambiguous-audit-turn-1", firstPrompt),
                        new EvaluationTurn("ambiguous-audit-turn-2", repeatedPrompt)),
                null,
                SetUtil.of("spend_by_category"),
                SetUtil.of("spend_by_category"),
                SetUtil.of(),
                List.of(AllowedToolPath.exact("spend_by_category")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                SetUtil.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));

        List<EvaluationRunResult> results = runner(chatUseCase, auditSource)
                .run("eval-ambiguous", userId, List.of(twoTurnCase));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).requestCorrelationId()).isEqualTo(auditUuid("audit-before-a").toString());
        assertThat(results.get(1).requestCorrelationId()).isNull();
        assertThat(results.get(1).systemOutcomePassed()).isFalse();
        assertThat(results.get(1).failureReasons())
                .contains(EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS);
        assertThat(results.get(1).requestCorrelationId())
                .isNotEqualTo(auditUuid("audit-ambiguous-b").toString())
                .isNotEqualTo(auditUuid("audit-ambiguous-c").toString());
    }

    @Test
    void normalUniqueNewAuditSucceedsAfterBeforeSnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation,
                        "How much did I spend on food in June 2026?", "spend_by_category", "audit-normal-a"))),
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation,
                        "What about May?", "spend_by_category", "audit-normal-b"))),
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation,
                        "What about April?", "spend_by_category", "audit-normal-c")))));
        EvaluationCase threeTurnCase = new EvaluationCase(
                "normal-unique",
                EvaluationCategory.COMPARISON,
                List.of(
                        new EvaluationTurn("normal-unique-turn-1", "How much did I spend on food in June 2026?"),
                        new EvaluationTurn("normal-unique-turn-2", "What about May?"),
                        new EvaluationTurn("normal-unique-turn-3", "What about April?")),
                null,
                SetUtil.of("spend_by_category"),
                SetUtil.of("spend_by_category"),
                SetUtil.of(),
                List.of(AllowedToolPath.exact("spend_by_category")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                SetUtil.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));

        List<EvaluationRunResult> results = runner(chatUseCase, auditSource)
                .run("eval-normal", userId, List.of(threeTurnCase));

        assertThat(results).hasSize(3);
        assertThat(results).extracting(EvaluationRunResult::requestCorrelationId)
                .containsExactly(
                        auditUuid("audit-normal-a").toString(),
                        auditUuid("audit-normal-b").toString(),
                        auditUuid("audit-normal-c").toString());
        assertThat(results).allSatisfy(result -> assertThat(result.systemOutcomePassed()).isTrue());
    }

    @Test
    void auditCorrelationDoesNotCrossCases() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationA = UUID.randomUUID();
        UUID conversationB = UUID.randomUUID();
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(
                scriptedResponse(conversationA, () -> auditSource.add(audit(userId, conversationA,
                        "How much did I spend on food in June 2026?", "spend_by_category", "audit-case-a"))),
                scriptedResponse(conversationB, () -> auditSource.add(audit(userId, conversationB,
                        "Show my recent Amazon transactions.", "recent_transactions", "audit-case-b")))));

        List<EvaluationRunResult> results = runner(chatUseCase, auditSource)
                .run("eval-correlation", userId, List.of(
                        SyntheticEvaluationDataset.smallPhase5BDataset().get(0),
                        SyntheticEvaluationDataset.smallPhase5BDataset().get(1)));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).conversationId()).isEqualTo(conversationA);
        assertThat(results.get(0).requestCorrelationId()).isEqualTo(auditUuid("audit-case-a").toString());
        assertThat(results.get(0).modelToolCalls()).extracting(ToolInvocationTrace::toolName)
                .containsExactly("spend_by_category");
        assertThat(results.get(1).conversationId()).isEqualTo(conversationB);
        assertThat(results.get(1).requestCorrelationId()).isEqualTo(auditUuid("audit-case-b").toString());
        assertThat(results.get(1).modelToolCalls()).extracting(ToolInvocationTrace::toolName)
                .containsExactly("recent_transactions");
    }

    @Test
    void followUpConversationPreservesOrderingAndDistinctAuditIds() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation,
                        "How much did I spend on food in June 2026?", "spend_by_category", "audit-turn-1"))),
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation,
                        "What about May?", "spend_by_category", "audit-turn-2")))));
        EvaluationCase followUp = SyntheticEvaluationDataset.smallPhase5BDataset().stream()
                .filter(evaluationCase -> evaluationCase.id().equals("follow-up-food-months"))
                .findFirst()
                .orElseThrow();

        List<EvaluationRunResult> results = runner(chatUseCase, auditSource)
                .run("eval-follow-up", userId, List.of(followUp));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(EvaluationRunResult::conversationId)
                .containsExactly(conversation, conversation);
        assertThat(results).extracting(EvaluationRunResult::attemptNumber)
                .containsExactly(1, 2);
        assertThat(results).extracting(EvaluationRunResult::requestCorrelationId)
                .containsExactly(auditUuid("audit-turn-1").toString(), auditUuid("audit-turn-2").toString());
        assertThat(results.get(0).requestCorrelationId()).isNotEqualTo(results.get(1).requestCorrelationId());
    }

    @Test
    void foreignTenantAuditsAreIgnoredBySnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID foreignUserId = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        String prompt = "What about May?";
        InMemoryAuditSource auditSource = new InMemoryAuditSource();
        auditSource.add(audit(foreignUserId, conversation, prompt, "savings_projector", "foreign-audit"));
        FakeChatUseCase chatUseCase = new FakeChatUseCase(List.of(
                scriptedResponse(conversation, () -> auditSource.add(audit(userId, conversation, prompt,
                        "spend_by_category", "tenant-audit")))));

        EvaluationRunResult result = runner(chatUseCase, auditSource)
                .run("eval-tenant", userId, List.of(singleToolCase("tenant-case", prompt)))
                .get(0);

        assertThat(result.requestCorrelationId()).isEqualTo(auditUuid("tenant-audit").toString());
        assertThat(result.modelToolCalls()).extracting(ToolInvocationTrace::toolName)
                .containsExactly("spend_by_category");
    }

    private EvaluationRunner runner(ChatUseCase chatUseCase, EvaluationAuditSource auditSource) {
        return new EvaluationRunner(
                chatUseCase,
                auditSource,
                new IntentClassifier(),
                objectMapper,
                new EvaluationScorer(),
                () -> "synthetic-model",
                null);
    }

    private EvaluationCase singleToolCase(String id, String prompt) {
        return EvaluationCase.singleTurn(
                id,
                EvaluationCategory.AGGREGATE,
                prompt,
                IntentBucket.AGGREGATE,
                SetUtil.of("spend_by_category"),
                SetUtil.of("spend_by_category"),
                SetUtil.of(),
                List.of(AllowedToolPath.exact("spend_by_category")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                SetUtil.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));
    }

    private ScriptedResponse scriptedResponse(UUID conversationId, ThrowingRunnable sideEffect) {
        return new ScriptedResponse(
                new ChatResponse(
                        conversationId,
                        "Verified answer",
                        List.of(),
                        new TokenUsageMetaData(10, 5, 15, true, false)),
                sideEffect);
    }

    private ChatAuditLogEntity audit(
            UUID userId,
            UUID conversationId,
            String message,
            String toolName,
            String auditId
    ) throws Exception {
        ChatAuditLogEntity audit = new ChatAuditLogEntity();
        audit.setId(auditUuid(auditId));
        audit.setUserId(userId);
        audit.setConversationId(conversationId);
        audit.setUserMessage(message);
        audit.setFinalAnswer("Verified answer");
        audit.setPromptTokens(10);
        audit.setCompletionTokens(5);
        audit.setTotalTokens(15);
        audit.setFlaggedHallucination(false);
        audit.setToolTurns(1);
        audit.setCreatedAt(LocalDateTime.now());
        audit.setToolCalls(objectMapper.writeValueAsString(List.of(
                Map.of(
                        "stage", "TOOL",
                        "invocationSource", "MODEL",
                        "toolName", toolName,
                        "arguments", Map.of(),
                        "status", "SUCCESS"),
                Map.of(
                        "stage", "VALIDATION",
                        "numericStatus", "PASSED",
                        "citationStatus", "PASSED",
                        "semanticStatus", "PASSED",
                        "unsupportedNumericClaims", List.of()))));
        return audit;
    }

    private UUID auditUuid(String auditId) {
        return UUID.nameUUIDFromBytes(auditId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record ScriptedResponse(
            ChatResponse response,
            ThrowingRunnable sideEffect
    ) {
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class FakeChatUseCase implements ChatUseCase {
        private final List<ScriptedResponse> responses;
        private int index;

        private FakeChatUseCase(List<ScriptedResponse> responses) {
            this.responses = responses;
        }

        @Override
        public ChatResponse processChat(UUID userId, ChatRequest request) {
            ScriptedResponse scripted = responses.get(index++);
            try {
                scripted.sideEffect().run();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return scripted.response();
        }
    }

    private static class InMemoryAuditSource implements EvaluationAuditSource {
        private final List<ChatAuditLogEntity> audits = new ArrayList<>();

        private void add(ChatAuditLogEntity audit) {
            audits.add(audit);
        }

        @Override
        public List<ChatAuditLogEntity> auditsForConversation(UUID userId, UUID conversationId) {
            if (userId == null || conversationId == null) {
                return List.of();
            }
            return audits.stream()
                    .filter(audit -> audit.getUserId().equals(userId))
                    .filter(audit -> audit.getConversationId().equals(conversationId))
                    .toList();
        }
    }

    private static final class SetUtil {
        private SetUtil() {
        }

        @SafeVarargs
        private static <T> java.util.Set<T> of(T... values) {
            return java.util.Set.of(values);
        }
    }
}
