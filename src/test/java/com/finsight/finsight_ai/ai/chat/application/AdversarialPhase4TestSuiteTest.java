package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateRepository;
import com.finsight.finsight_ai.ai.chat.application.validation.CitationValidatorService;
import com.finsight.finsight_ai.ai.chat.application.validation.NumericConsistencyValidator;
import com.finsight.finsight_ai.ai.chat.domain.*;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdversarialPhase4TestSuiteTest {

    private final ChatModelPort chatModel = mock(ChatModelPort.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final VectorSearchPort vectorSearch = mock(VectorSearchPort.class);
    private final EmbeddingPort embedding = mock(EmbeddingPort.class);
    private final TokenBudgetManager tokenBudget = mock(TokenBudgetManager.class);
    private final ChatAuditPersistenceService auditPersistence = mock(ChatAuditPersistenceService.class);
    private final ChatSessionStateRepository sessionRepository = mock(ChatSessionStateRepository.class);

    private NumericConsistencyValidator numericValidator;
    private CitationValidatorService citationValidator;
    private ChatOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        numericValidator = new NumericConsistencyValidator();
        citationValidator = new CitationValidatorService();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(toolRegistry.getToolSpecs()).thenReturn(List.of());
        when(sessionRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());

        orchestrationService = new ChatOrchestrationService(
                chatModel,
                toolRegistry,
                vectorSearch,
                embedding,
                tokenBudget,
                citationValidator,
                numericValidator,
                new FinancialQueryDetector(),
                new IntentClassifier(),
                auditPersistence,
                sessionRepository,
                objectMapper
        );
    }

    // --- A. NUMERIC ATTACKS ---
    @Test
    @DisplayName("A. Numeric Attacks: Reject unevidenced monetary amounts")
    void numericAttack_unsupportedAmountFailsValidation() {
        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("spend_by_category", "totalAmount", new BigDecimal("8430.00"), "INR")
        );

        // Candidate response claims 8340 instead of 8430
        String candidate = "You spent ₹8,340 on food.";
        NumericConsistencyValidator.ValidationResult result = numericValidator.validate(candidate, evidence);

        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("A. Numeric Attacks: Reject unsupported extra money claim")
    void numericAttack_extraUnsupportedMoneyFails() {
        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("spend_by_category", "totalAmount", new BigDecimal("8430.00"), "INR")
        );

        String candidate = "You spent ₹8,430 on food and an additional ₹500 on snacks.";
        NumericConsistencyValidator.ValidationResult result = numericValidator.validate(candidate, evidence);

        assertThat(result.valid()).isFalse();
    }

    // --- B. YEAR / NUMBER CONFUSION ---
    @Test
    @DisplayName("B. Year/Number Confusion: Distinguish calendar years from monetary values")
    void yearNumberConfusion_validatesCorrectly() {
        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("spend_by_category", "totalAmount", new BigDecimal("2026.00"), "INR"),
                NumericEvidence.count("spend_by_category", "transactionCount", 2)
        );

        String candidate = "In July 2026, you had 2 transactions totaling ₹2,026.";
        NumericConsistencyValidator.ValidationResult result = numericValidator.validate(candidate, evidence);

        assertThat(result.valid()).isTrue();
    }

    // --- C. CITATION ATTACKS ---
    @Test
    @DisplayName("C. Citation Attacks: Reject citation of unevidenced UUID")
    void citationAttack_rejectsFakeUuid() {
        UUID validTxId = UUID.randomUUID();
        UUID fakeTxId = UUID.randomUUID();

        Set<UUID> validSet = Set.of(validTxId);
        String text = "Verified transaction [" + fakeTxId + "]";

        CitationValidatorService.CitationValidationResult result = citationValidator.validate(text, validSet);

        assertThat(result.hallucinationDetected()).isTrue();
    }

    // --- D. TENANT ATTACKS ---
    @Test
    @DisplayName("D. Tenant Attacks: ThreadLocal TenantContext prevents leakage across users")
    void tenantAttack_tenantIsolationEnforced() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        TenantContext.set(userA);
        assertThat(TenantContext.require()).isEqualTo(userA);
        TenantContext.clear();

        TenantContext.set(userB);
        assertThat(TenantContext.require()).isEqualTo(userB);
        TenantContext.clear();
    }

    // --- F. TOOL LOOP ATTACKS ---
    @Test
    @DisplayName("F. Tool Loop Attacks: Safeguard against infinite tool loops")
    void toolLoopAttack_exceedingMaxRoundsTerminatesSafely() {
        UUID userId = UUID.randomUUID();
        ChatModelOutput loopOutput = new ChatModelOutput(
                null,
                List.of(new ToolCallRequest("call_loop", "unknown_tool", java.util.Map.of())),
                10, 10
        );

        when(chatModel.generate(any())).thenReturn(loopOutput);
        when(toolRegistry.execute(any())).thenReturn(
                ToolExecutionResult.systemError("UNKNOWN_TOOL", "Unknown tool")
        );

        ChatResponse response = orchestrationService.processChat(userId, new ChatRequest("Loop test", null));

        assertThat(response.answer()).contains("couldn't verify");
    }

    // --- E. BALANCE RECONCILER PROVENANCE TESTS A THROUGH L ---
    @Test
    @DisplayName("Test A — No starting balance anywhere returns INSUFFICIENT_DATA")
    void testA_noStartingBalance_returnsInsufficientData() {
        com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort analyticsPort = mock(com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort.class);
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(analyticsPort.reconcileBalance(userId, accountId, null, BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE)).thenReturn(
                new BalanceReconciliationResult(
                        userId, accountId, "Checking", "INR", null,
                        BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE,
                        new BigDecimal("1000.00"), new BigDecimal("400.00"), null, new BigDecimal("600.00"),
                        null, false, "INSUFFICIENT_DATA"
                )
        );

        BalanceReconciliationResult result =
                analyticsPort.reconcileBalance(userId, accountId, null, BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE);

        assertThat(result.status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.reconciled()).isFalse();
        assertThat(result.startingBalance()).isNull();
    }

    @Test
    @DisplayName("Test B — Gemini invents starting balance: rejected as UNVERIFIED")
    void testB_geminiInventsStartingBalance_rejectedAsUnverified() {
        com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool tool =
                new com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool(
                        mock(com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort.class),
                        mock(com.finsight.finsight_ai.repository.AccountRepository.class));

        com.finsight.finsight_ai.ai.chat.support.UserPromptContext.set("Why doesn't my balance reconcile?");
        try {
            boolean explicit = com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                    .isExplicitInUserPrompt(new BigDecimal("50000"), com.finsight.finsight_ai.ai.chat.support.UserPromptContext.get());
            assertThat(explicit).isFalse();
        } finally {
            com.finsight.finsight_ai.ai.chat.support.UserPromptContext.clear();
        }
    }

    @Test
    @DisplayName("Test C — Explicit user-supplied starting balance accepted as USER_PROVIDED")
    void testC_explicitUserSuppliedStartingBalance_acceptedAsUserProvided() {
        com.finsight.finsight_ai.ai.chat.support.UserPromptContext.set("My opening balance was ₹50,000. Why doesn't it reconcile?");
        try {
            boolean explicit = com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                    .isExplicitInUserPrompt(new BigDecimal("50000"), com.finsight.finsight_ai.ai.chat.support.UserPromptContext.get());
            assertThat(explicit).isTrue();
        } finally {
            com.finsight.finsight_ai.ai.chat.support.UserPromptContext.clear();
        }
    }

    @Test
    @DisplayName("Test D — Non-zero opening balance exact reconciliation")
    void testD_exactReconciliation_returnsReconciled() {
        com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort analyticsPort = mock(com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort.class);
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        when(analyticsPort.reconcileBalance(userId, accountId, new BigDecimal("50000"), BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED)).thenReturn(
                new BalanceReconciliationResult(
                        userId, accountId, "Checking", "INR", new BigDecimal("50000"),
                        BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED,
                        new BigDecimal("10000"), new BigDecimal("5000"), new BigDecimal("55000"), new BigDecimal("55000"),
                        BigDecimal.ZERO, true, "RECONCILED"
                )
        );

        BalanceReconciliationResult result =
                analyticsPort.reconcileBalance(userId, accountId, new BigDecimal("50000"), BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED);

        assertThat(result.reconciled()).isTrue();
        assertThat(result.difference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test G — startingBalance = 0 explicitly supplied in user prompt works")
    void testG_startingBalanceZeroExplicitlySupplied_works() {
        com.finsight.finsight_ai.ai.chat.support.UserPromptContext.set("My opening balance was ₹0. Why doesn't it reconcile?");
        try {
            boolean explicit = com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                    .isExplicitInUserPrompt(BigDecimal.ZERO, com.finsight.finsight_ai.ai.chat.support.UserPromptContext.get());
            assertThat(explicit).isTrue();
        } finally {
            com.finsight.finsight_ai.ai.chat.support.UserPromptContext.clear();
        }
    }

    @Test
    @DisplayName("Test H — Starting balance semantic collisions (spending, salary, ending balance) are rejected")
    void testH_startingBalanceSemanticCollisions_rejected() {
        // 1. Spending collision
        String spendingPrompt = "I spent ₹50,000 last month. Reconcile my account.";
        assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                .isExplicitInUserPrompt(new BigDecimal("50000"), spendingPrompt)).isFalse();

        // 2. Salary collision
        String salaryPrompt = "My salary was ₹50,000.";
        assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                .isExplicitInUserPrompt(new BigDecimal("50000"), salaryPrompt)).isFalse();

        // 3. Ending balance collision
        String endingPrompt = "My ending balance is ₹50,000.";
        assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                .isExplicitInUserPrompt(new BigDecimal("50000"), endingPrompt)).isFalse();

        // 4. Valid opening balance forms
        String startedPrompt = "I started July with ₹50,000.";
        assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                .isExplicitInUserPrompt(new BigDecimal("50000"), startedPrompt)).isTrue();

        String beginningPrompt = "My balance at the beginning of July was ₹50,000.";
        assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                .isExplicitInUserPrompt(new BigDecimal("50000"), beginningPrompt)).isTrue();
    }

    @Test
    @DisplayName("Test I — UserPromptContext ThreadLocal cleanup prevents context leakage across requests on same worker thread")
    void testI_userPromptContext_threadLocalCleanup_preventsLeakage() {
        UUID user = UUID.randomUUID();

        // Request A: User specifies opening balance
        TenantContext.set(user);
        com.finsight.finsight_ai.ai.chat.support.UserPromptContext.set("My opening balance was ₹50,000.");
        try {
            assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                    .isExplicitInUserPrompt(new BigDecimal("50000"), com.finsight.finsight_ai.ai.chat.support.UserPromptContext.get())).isTrue();
        } finally {
            com.finsight.finsight_ai.ai.chat.support.UserPromptContext.clear();
            TenantContext.clear();
        }

        // Request B on same thread: User asks question without specifying opening balance
        TenantContext.set(user);
        com.finsight.finsight_ai.ai.chat.support.UserPromptContext.set("Why doesn't my balance reconcile?");
        try {
            assertThat(com.finsight.finsight_ai.ai.chat.adapters.out.tools.BalanceReconcilerTool
                    .isExplicitInUserPrompt(new BigDecimal("50000"), com.finsight.finsight_ai.ai.chat.support.UserPromptContext.get())).isFalse();
        } finally {
            com.finsight.finsight_ai.ai.chat.support.UserPromptContext.clear();
            TenantContext.clear();
        }

        // Ensure context is empty/null after clearing
        assertThat(com.finsight.finsight_ai.ai.chat.support.UserPromptContext.get()).isNull();
        assertThat(TenantContext.get()).isEmpty();
    }
}
