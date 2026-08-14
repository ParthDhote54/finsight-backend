package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateRepository;
import com.finsight.finsight_ai.ai.chat.application.validation.CitationValidatorService;
import com.finsight.finsight_ai.ai.chat.application.validation.NumericConsistencyValidator;
import com.finsight.finsight_ai.ai.chat.domain.*;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatOrchestrationToolLoopTest {

    private final ChatModelPort chatModel = mock(ChatModelPort.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final VectorSearchPort vectorSearch = mock(VectorSearchPort.class);
    private final EmbeddingPort embedding = mock(EmbeddingPort.class);
    private final TokenBudgetManager tokenBudget = mock(TokenBudgetManager.class);
    private final ChatAuditPersistenceService auditPersistence = mock(ChatAuditPersistenceService.class);
    private final ChatSessionStateRepository sessionRepository = mock(ChatSessionStateRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort financialAnalyticsPort = mock(com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort.class);
    private final com.finsight.finsight_ai.repository.TransactionRepository transactionRepository = mock(com.finsight.finsight_ai.repository.TransactionRepository.class);
    private ChatOrchestrationService service;

    @BeforeEach
    void setUp() {
        when(toolRegistry.getToolSpecs()).thenReturn(List.of());
        when(sessionRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(embedding.embed(any())).thenReturn(new float[0]);
        when(vectorSearch.similaritySearch(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(tokenBudget.trimToFit(any(), any(), any())).thenReturn(List.of());
        service = new ChatOrchestrationService(
                chatModel,
                toolRegistry,
                vectorSearch,
                embedding,
                tokenBudget,
                new CitationValidatorService(),
                new NumericConsistencyValidator(),
                new FinancialQueryDetector(),
                new IntentClassifier(),
                auditPersistence,
                sessionRepository,
                objectMapper,
                financialAnalyticsPort,
                transactionRepository);
    }

    @Test
    void preservesStructuredHistoryAcrossTwoToolRounds() {
        ToolCallRequest firstCall = call("call-1", "compare_months");
        ToolCallRequest secondCall = call("call-2", "merchant_breakdown");
        when(chatModel.generate(any()))
                .thenReturn(output(firstCall), output(secondCall), finalOutput("Verified explanation"));
        when(toolRegistry.execute(firstCall)).thenReturn(ToolExecutionResult.success("{\"period1Total\":10}"));
        when(toolRegistry.execute(secondCall)).thenReturn(ToolExecutionResult.success("{\"items\":[]}"));

        ChatResponse response = service.processChat(
                UUID.randomUUID(), new ChatRequest("Why did spending increase?", null));

        assertThat(response.answer()).isEqualTo("Verified explanation");
        assertThat(response.metaData().usedTool()).isTrue();
        ArgumentCaptor<ChatModelInput> inputs = ArgumentCaptor.forClass(ChatModelInput.class);
        verify(chatModel, times(3)).generate(inputs.capture());
        assertThat(inputs.getAllValues()).allSatisfy(input ->
                assertThat(input.history()).anySatisfy(turn -> {
                    assertThat(turn.role()).isEqualTo(Role.USER);
                    assertThat(turn.content()).isEqualTo("Why did spending increase?");
                }));
        assertThat(inputs.getAllValues().get(1).history())
                .anySatisfy(turn -> assertThat(turn.toolCalls()).containsExactly(firstCall))
                .anySatisfy(turn -> assertThat(turn.toolResults()).singleElement()
                        .satisfies(result -> assertThat(result.callId()).isEqualTo("call-1")));
        assertThat(inputs.getAllValues().get(2).history())
                .anySatisfy(turn -> assertThat(turn.toolCalls()).containsExactly(secondCall));
    }

    @Test
    void moreThanFiveCallsExecutesNoneAndTerminatesSafely() {
        List<ToolCallRequest> calls = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            calls.add(call("call-" + index, "top_merchants"));
        }
        when(chatModel.generate(any())).thenReturn(new ChatModelOutput("", calls, 1, 1));

        ChatResponse response = service.processChat(
                UUID.randomUUID(), new ChatRequest("Show spending totals", null));

        assertThat(response.answer()).isEqualTo("I couldn't verify this from your financial data.");
        verify(toolRegistry, never()).execute(any());
        verify(chatModel, times(1)).generate(any());
    }

    @Test
    void fourthRequestedRoundIsNotExecuted() {
        ToolCallRequest first = call("call-1", "top_merchants");
        ToolCallRequest second = call("call-2", "top_merchants");
        ToolCallRequest third = call("call-3", "top_merchants");
        ToolCallRequest fourth = call("call-4", "top_merchants");
        when(chatModel.generate(any())).thenReturn(output(first), output(second), output(third), output(fourth));
        when(toolRegistry.execute(any())).thenReturn(ToolExecutionResult.success("{\"merchants\":[]}"));

        ChatResponse response = service.processChat(
                UUID.randomUUID(), new ChatRequest("Show spending totals", null));

        assertThat(response.answer()).isEqualTo("I couldn't verify this from your financial data.");
        verify(toolRegistry, times(3)).execute(any());
        verify(toolRegistry, never()).execute(fourth);
        verify(chatModel, times(4)).generate(any());
    }

    @Test
    void modelToolMissRecoveryIsAuditedSeparatelyFromModelSelection() throws Exception {
        when(toolRegistry.getToolSpecs()).thenReturn(List.of(spendingDeltaSpec()));
        when(chatModel.generate(any()))
                .thenReturn(finalOutput("I need a tool."), finalOutput("Delta was INR 8,500."));
        when(toolRegistry.execute(any())).thenReturn(ToolExecutionResult.success(
                "{\"delta\":8500,\"currency\":\"INR\"}",
                List.of(NumericEvidence.monetary("spending_delta_explainer", "delta", new BigDecimal("8500"), "INR")),
                Set.of()));

        ChatResponse response = service.processChat(UUID.randomUUID(), new ChatRequest(
                "Why did my food spending increase in June 2026 compared to May 2026?", null));

        assertThat(response.answer()).isEqualTo("Delta was INR 8,500.");
        ArgumentCaptor<ToolCallRequest> toolCall = ArgumentCaptor.forClass(ToolCallRequest.class);
        verify(toolRegistry).execute(toolCall.capture());
        assertThat(toolCall.getValue().toolName()).isEqualTo("spending_delta_explainer");
        assertThat(toolCall.getValue().arguments()).containsEntry("periodA", "2026-05")
                .containsEntry("periodB", "2026-06")
                .containsEntry("categoryOrGroup", "food");

        String trace = persistedAudit().getToolCalls();
        assertThat(trace).contains("\"stage\":\"DETERMINISTIC_RECOVERY\"");
        assertThat(trace).contains("\"modelToolRequested\":false");
        assertThat(trace).contains("\"invocationSource\":\"DETERMINISTIC_RECOVERY\"");
    }

    @Test
    void modelSelectedToolDoesNotTriggerDeterministicRecoveryForSamePrompt() {
        when(toolRegistry.getToolSpecs()).thenReturn(List.of(spendingDeltaSpec(), topMerchantsSpec()));
        ToolCallRequest modelCall = call("call-1", "top_merchants");
        when(chatModel.generate(any()))
                .thenReturn(output(modelCall), finalOutput("Verified without numeric claims."));
        when(toolRegistry.execute(modelCall)).thenReturn(ToolExecutionResult.success("{\"merchants\":[]}"));

        ChatResponse response = service.processChat(UUID.randomUUID(), new ChatRequest(
                "Why did my food spending increase in June 2026 compared to May 2026?", null));

        assertThat(response.answer()).isEqualTo("Verified without numeric claims.");
        verify(toolRegistry, times(1)).execute(modelCall);
        assertThat(persistedAudit().getToolCalls())
                .contains("\"invocationSource\":\"MODEL\"")
                .doesNotContain("\"stage\":\"DETERMINISTIC_RECOVERY\"");
    }

    @Test
    void spendingDeltaRecoveryDoesNotInventMissingOrGeneralParameters() {
        when(toolRegistry.getToolSpecs()).thenReturn(List.of(spendingDeltaSpec()));
        when(chatModel.generate(any())).thenReturn(finalOutput("I need more context."));

        service.processChat(UUID.randomUUID(), new ChatRequest("Why did it increase?", null));
        service.processChat(UUID.randomUUID(), new ChatRequest("Why is inflation increasing?", null));

        verify(toolRegistry, never()).execute(any());
    }

    @Test
    void balanceRecoveryPreservesExplicitOpeningBalanceAndRejectsCollisionAsUnavailable() {
        when(toolRegistry.getToolSpecs()).thenReturn(List.of(balanceSpec()));
        when(chatModel.generate(any()))
                .thenReturn(finalOutput("Need reconciliation."), finalOutput("Done."))
                .thenReturn(finalOutput("Need reconciliation."), finalOutput("Need starting balance."));
        when(toolRegistry.execute(any())).thenReturn(ToolExecutionResult.success(
                "{\"reconciled\":false,\"totalExpense\":0,\"startingBalanceSource\":\"USER_PROVIDED\"}"));

        service.processChat(UUID.randomUUID(), new ChatRequest(
                "My opening balance was INR 50,000. Why doesn't it reconcile?", null));
        service.processChat(UUID.randomUUID(), new ChatRequest(
                "I spent INR 50,000 last month. Why doesn't it reconcile?", null));

        ArgumentCaptor<ToolCallRequest> calls = ArgumentCaptor.forClass(ToolCallRequest.class);
        verify(toolRegistry, times(2)).execute(calls.capture());
        assertThat(calls.getAllValues().get(0).arguments()).containsEntry("startingBalance", new BigDecimal("50000"));
        assertThat(calls.getAllValues().get(1).arguments()).isEmpty();
    }

    @Test
    void reconciliationSemanticGuardAllowsAndRejectsStatusContradictions() {
        assertBalanceAnswer(new BalanceFixture(true, "Account is reconciled."), "Account is reconciled.");
        assertBalanceAnswer(new BalanceFixture(false, "There is a discrepancy."), "There is a discrepancy.");
        assertBalanceAnswer(new BalanceFixture(false, "The account is reconciled."), "I couldn't verify this from your financial data.");
        assertBalanceAnswer(new BalanceFixture(true, "The account does not reconcile."), "I couldn't verify this from your financial data.");
    }

    private static ToolCallRequest call(String id, String name) {
        return new ToolCallRequest(id, name, Map.of());
    }

    private static ChatModelOutput output(ToolCallRequest call) {
        return new ChatModelOutput("", List.of(call), 1, 1);
    }

    private static ChatModelOutput finalOutput(String text) {
        return new ChatModelOutput(text, List.of(), 1, 1);
    }

    private ChatAuditLogEntity persistedAudit() {
        ArgumentCaptor<ChatAuditLogEntity> audit = ArgumentCaptor.forClass(ChatAuditLogEntity.class);
        verify(auditPersistence, atLeastOnce()).persist(audit.capture());
        return audit.getAllValues().get(audit.getAllValues().size() - 1);
    }

    private void assertBalanceAnswer(BalanceFixture fixture, String expectedAnswer) {
        reset(chatModel, toolRegistry, auditPersistence, sessionRepository);
        when(toolRegistry.getToolSpecs()).thenReturn(List.of(balanceSpec()));
        when(sessionRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(chatModel.generate(any()))
                .thenReturn(finalOutput("Need reconciliation."), finalOutput(fixture.answer()), finalOutput(""));
        when(toolRegistry.execute(any())).thenReturn(ToolExecutionResult.success(
                "{\"reconciled\":" + fixture.reconciled() + ",\"totalExpense\":0,\"startingBalanceSource\":\"USER_PROVIDED\"}"));

        ChatResponse response = service.processChat(UUID.randomUUID(), new ChatRequest(
                "My opening balance was INR 50,000. Why doesn't it reconcile?", null));

        assertThat(response.answer()).isEqualTo(expectedAnswer);
    }

    private static ToolSpec spendingDeltaSpec() {
        return new ToolSpec("spending_delta_explainer", "delta", "{}");
    }

    private static ToolSpec topMerchantsSpec() {
        return new ToolSpec("top_merchants", "top", "{}");
    }

    private static ToolSpec balanceSpec() {
        return new ToolSpec("balance_reconciler", "balance", "{}");
    }

    private record BalanceFixture(boolean reconciled, String answer) {
    }
}
