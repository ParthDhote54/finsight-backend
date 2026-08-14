package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateRepository;
import com.finsight.finsight_ai.ai.chat.application.validation.CitationValidatorService;
import com.finsight.finsight_ai.ai.chat.application.validation.NumericConsistencyValidator;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelInput;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelOutput;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.domain.ChatTurn;
import com.finsight.finsight_ai.ai.chat.domain.CategorySpendResult;
import com.finsight.finsight_ai.ai.chat.domain.DialogueState;
import com.finsight.finsight_ai.ai.chat.domain.LowestCategoryResult;
import com.finsight.finsight_ai.ai.chat.domain.LowestTransactionResult;
import com.finsight.finsight_ai.ai.chat.domain.MerchantGroupSpendResult;
import com.finsight.finsight_ai.ai.chat.domain.TransactionSummaryResult;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LiveApiReplayTest {

    private ChatOrchestrationService chatOrchestrationService;
    private ObjectMapper objectMapper;
    private Clock clock;
    private UUID testUserId;
    private UUID testConversationId;
    private Map<UUID, ChatSessionStateEntity> sessionStore;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        clock = Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneId.of("UTC"));

        testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        testConversationId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        FinancialAnalyticsPort analyticsPort = mock(FinancialAnalyticsPort.class);
        when(analyticsPort.getSpendByMerchantGroup(eq(testUserId), eq("coffee"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(
                        testUserId, "coffee", new BigDecimal("130.00"), 2L, "INR",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        when(analyticsPort.getSpendByMerchantGroup(eq(testUserId), eq("pizza"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(
                        testUserId, "pizza", new BigDecimal("450.00"), 1L, "INR",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        when(analyticsPort.getLowestCategorySpend(eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new LowestCategoryResult(
                        testUserId, "Coffee Shops", new BigDecimal("130.00"), "INR",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), true));
        when(analyticsPort.getLowestTransaction(eq(testUserId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new LowestTransactionResult(
                        testUserId,
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "cutting chai",
                        new BigDecimal("10.00"),
                        "Coffee Shops",
                        "INR",
                        LocalDate.of(2026, 8, 11),
                        true));
        when(analyticsPort.getSpendByCategory(eq(testUserId), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new CategorySpendResult(
                        testUserId, "all", new BigDecimal("730.00"), 4L, "INR",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        "Pizza", new BigDecimal("450.00")));
        when(analyticsPort.getRecentTransactions(eq(testUserId), org.mockito.ArgumentMatchers.nullable(String.class), anyInt()))
                .thenReturn(List.of(new TransactionSummaryResult(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        LocalDate.of(2026, 8, 11),
                        "cutting chai",
                        new BigDecimal("10.00"),
                        "INR",
                        "Coffee Shops",
                        TransactionType.EXPENSE)));

        sessionStore = new HashMap<>();
        ChatSessionStateRepository sessionStateRepository = mock(ChatSessionStateRepository.class);
        when(sessionStateRepository.findByConversationIdAndUserId(any(), any())).thenAnswer(inv -> {
            UUID cid = inv.getArgument(0);
            return Optional.ofNullable(sessionStore.get(cid));
        });
        when(sessionStateRepository.save(any())).thenAnswer(inv -> {
            ChatSessionStateEntity entity = inv.getArgument(0);
            if (entity.getConversationId() == null) {
                entity.setConversationId(UUID.randomUUID());
            }
            sessionStore.put(entity.getConversationId(), entity);
            return entity;
        });

        ChatModelPort chatModelPort = mock(ChatModelPort.class);
        when(chatModelPort.generate(any())).thenAnswer(inv -> {
            ChatModelInput input = inv.getArgument(0);
            List<ChatTurn> history = input.history() != null ? input.history() : List.of();
            String lastTurnStr = !history.isEmpty() ? history.get(history.size() - 1).toString() : "";

            if (lastTurnStr.contains("spend_by_category") || lastTurnStr.contains("largestCategory")) {
                return new ChatModelOutput(
                        "In August 2026, your total category spending was \u20b9730.00 across 4 transactions, with your largest spending in Pizza at \u20b9450.00.",
                        List.of(),
                        10,
                        10
                );
            }
            if (lastTurnStr.contains("recent_transactions") || lastTurnStr.contains("cutting chai")) {
                return new ChatModelOutput(
                        "Your recent transactions for August 2026 include cutting chai for \u20b910.00 on 2026-08-11.",
                        List.of(),
                        10,
                        10
                );
            }
            return new ChatModelOutput("Model text answer", List.of(), 10, 10);
        });

        com.finsight.finsight_ai.ai.chat.adapters.out.tools.ToolMonthParser monthParser =
                new com.finsight.finsight_ai.ai.chat.adapters.out.tools.ToolMonthParser(clock);
        com.finsight.finsight_ai.ai.chat.adapters.out.tools.RecentTransactionsTool recentTxTool =
                new com.finsight.finsight_ai.ai.chat.adapters.out.tools.RecentTransactionsTool(analyticsPort);
        com.finsight.finsight_ai.ai.chat.adapters.out.tools.SpendByCategoryTool categoryTool =
                new com.finsight.finsight_ai.ai.chat.adapters.out.tools.SpendByCategoryTool(analyticsPort, monthParser);
        com.finsight.finsight_ai.ai.chat.adapters.out.tools.SpendByMerchantGroupTool merchantGroupTool =
                new com.finsight.finsight_ai.ai.chat.adapters.out.tools.SpendByMerchantGroupTool(analyticsPort, monthParser);

        ToolRegistry toolRegistry = new ToolRegistry(List.of(recentTxTool, categoryTool, merchantGroupTool), objectMapper);
        VectorSearchPort vectorSearchPort = mock(VectorSearchPort.class);
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        TokenBudgetManager tokenBudgetManager = new TokenBudgetManager();
        CitationValidatorService citationValidatorService = new CitationValidatorService();
        NumericConsistencyValidator numericConsistencyValidator = new NumericConsistencyValidator();
        FinancialQueryDetector financialQueryDetector = new FinancialQueryDetector();
        IntentClassifier intentClassifier = new IntentClassifier();
        ConversationalQueryResolver conversationalQueryResolver = new ConversationalQueryResolver(clock, objectMapper);
        ChatAuditPersistenceService auditPersistenceService = mock(ChatAuditPersistenceService.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);

        chatOrchestrationService = new ChatOrchestrationService(
                chatModelPort,
                toolRegistry,
                vectorSearchPort,
                embeddingPort,
                tokenBudgetManager,
                citationValidatorService,
                numericConsistencyValidator,
                financialQueryDetector,
                intentClassifier,
                conversationalQueryResolver,
                auditPersistenceService,
                sessionStateRepository,
                objectMapper,
                analyticsPort,
                transactionRepository,
                clock
        );
    }

    @Test
    void replay10TurnLiveTranscriptSequence() throws Exception {
        String[] prompts = new String[]{
                "i want my coffee spending vs pizza spending for this month",
                "lowest spending this month",
                "which category had lowest spending",
                "which category had lowest spending for this month",
                "why ??",
                "prime minister",
                "weather",
                "last transaction",
                "lowest transaction in this month",
                "current year"
        };

        ExpectedTurn[] expectedTurns = new ExpectedTurn[]{
                new ExpectedTurn(true, "compare_entities", List.of(
                        "spending on coffee was", "spending on pizza was", "more on pizza"
                ), List.of()),
                new ExpectedTurn(true, "lowest_category", List.of(
                        "lowest spending was Coffee Shops", "130.00"
                ), List.of()),
                new ExpectedTurn(true, "lowest_category", List.of(
                        "lowest spending was Coffee Shops", "130.00"
                ), List.of()),
                new ExpectedTurn(true, "lowest_category", List.of(
                        "lowest spending was Coffee Shops", "130.00"
                ), List.of()),
                new ExpectedTurn(false, null, List.of(
                        "could not be verified"
                ), List.of()),
                new ExpectedTurn(false, null, List.of(
                        "I am FinSight AI"
                ), List.of()),
                new ExpectedTurn(false, null, List.of(
                        "I am FinSight AI"
                ), List.of()),
                new ExpectedTurn(true, "recent_transactions", List.of(
                        "recent transactions for August 2026", "cutting chai", "10.00"
                ), List.of()),
                new ExpectedTurn(true, "lowest_transaction", List.of(
                        "lowest transaction in August 2026", "cutting chai", "10.00"
                ), List.of("total category spending")),
                new ExpectedTurn(true, "lowest_transaction", List.of(
                        "lowest transaction in August 2026", "cutting chai", "10.00"
                ), List.of("total category spending"))
        };

        for (int i = 0; i < prompts.length; i++) {
            int turnNum = i + 1;
            ChatResponse response = chatOrchestrationService.processChat(
                    testUserId,
                    new ChatRequest(prompts[i], testConversationId)
            );
            ExpectedTurn expected = expectedTurns[i];

            assertEquals(expected.usedTool(), response.metaData().usedTool(), "Turn " + turnNum + " usedTool mismatch");
            for (String requiredFragment : expected.requiredFragments()) {
                assertTrue(
                        response.answer().contains(requiredFragment),
                        "Turn " + turnNum + " answer missing fragment: " + requiredFragment + "\nActual: " + response.answer()
                );
            }
            for (String forbiddenFragment : expected.forbiddenFragments()) {
                assertFalse(
                        response.answer().contains(forbiddenFragment),
                        "Turn " + turnNum + " answer unexpectedly contained: " + forbiddenFragment + "\nActual: " + response.answer()
                );
            }

            if (expected.dialogueToolName() != null) {
                ChatSessionStateEntity session = sessionStore.get(testConversationId);
                assertNotNull(session, "Turn " + turnNum + " should have stored session state");
                DialogueState state = objectMapper.readValue(session.getDialogueState(), DialogueState.class);
                assertEquals(expected.dialogueToolName(), state.lastToolName(),
                        "Turn " + turnNum + " dialogue tool mismatch");
            }
        }
    }

    private record ExpectedTurn(
            boolean usedTool,
            String dialogueToolName,
            List<String> requiredFragments,
            List<String> forbiddenFragments
    ) {
    }
}
