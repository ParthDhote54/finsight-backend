package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateRepository;
import com.finsight.finsight_ai.ai.chat.application.validation.CitationValidatorService;
import com.finsight.finsight_ai.ai.chat.application.validation.NumericConsistencyValidator;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelInput;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelOutput;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.domain.DialogueState;
import com.finsight.finsight_ai.ai.chat.domain.ChatTurn;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.Role;
import com.finsight.finsight_ai.ai.chat.domain.TokenUsageMetaData;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallResult;
import com.finsight.finsight_ai.ai.chat.domain.ToolExecutionResult;
import com.finsight.finsight_ai.ai.chat.domain.ToolSpec;
import com.finsight.finsight_ai.ai.chat.domain.TransactionCitation;
import com.finsight.finsight_ai.ai.chat.ports.in.ChatUseCase;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import com.finsight.finsight_ai.ai.chat.support.UserPromptContext;

@Service
@Slf4j
public class ChatOrchestrationService implements ChatUseCase {

    private static final int MAX_TOOL_TURNS = 3;
    private static final int MAX_TOOL_CALLS_PER_TURN = 5;
    private static final String SAFE_UNVERIFIED_RESPONSE =
            "I couldn't verify this from your financial data.";
    private static final String DEFAULT_SYSTEM_PROMPT = """
            Identity: You are FinSight AI, a polished financial analysis assistant.
            Evidence rule: Never state an authoritative user-specific financial number without deterministic tool evidence.
            Tool-first rule: Use bounded finance tools for financial amounts/counts/comparisons/rankings. If tool evidence is returned in context (e.g. spend_by_category or top_merchants), answer directly using that tool evidence. Relative date expressions (this month, current month, last month) are automatically resolved by the system.
            Category spending rule: For overall or category spending questions ("Where did most of my money go", "spending breakdown"), focus on category totals from spend_by_category evidence. Highlight the largest spending category and breakdown. Do NOT mention individual merchant names unless a merchant tool was executed in the current turn.
            Reconciliation rule: For reconcile/reconciliation/balance discrepancy questions, call balance_reconciler before answering.
            Tone & Style: Be concise, direct, and natural. Never start responses with 'I can help you with that!' or ask repetitive generic questions like 'What would you like to know about your finances?'. Never ask for info already in dialogue state. Use the currency symbol/code returned in tool evidence (e.g. ₹ for INR, $ for USD, € for EUR).
            Citation rule: Only cite transaction IDs supplied as explicit evidence.
            """;
    private static final Pattern FUZZY_RETRIEVAL = Pattern.compile(
            "(?i)\\b(late[- ]night|date[- ]night|wellness|junk food|similar|related|fuzzy|vibe|kind of)\\b");
    private static final Pattern RECONCILIATION_REQUEST = Pattern.compile(
            "(?i)\\b(reconcile|reconciles|reconciled|reconciliation|discrepancy|balance discrepancy)\\b");
    private static final Pattern EXPLICIT_STARTING_BALANCE = Pattern.compile(
            "(?i)\\b(opening|starting|initial|beginning|start)\\s+balance\\D{0,40}([0-9][0-9,]*(?:\\.\\d+)?)");
    private static final String BALANCE_RECONCILER_TOOL = "balance_reconciler";
    private static final Pattern SPENDING_DELTA_REQUEST = Pattern.compile(
            "(?i)\\bwhy\\b.*\\b(spend|spending|expense|expenses)\\b.*\\b(increase|decrease|change|went up|went down)\\b");
    private static final Pattern CATEGORY_BEFORE_SPENDING = Pattern.compile(
            "(?i)\\bmy\\s+([a-z][a-z &-]{1,40}?)\\s+(?:spend|spending|expense|expenses)\\b");
    private static final Pattern MONTH_YEAR = Pattern.compile(
            "(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(20\\d{2})\\b");
    private static final Pattern POSITIVE_RECONCILED_CLAIM = Pattern.compile(
            "(?i)\\b(?:account|balance|everything|it|this)\\s+(?:is|was)\\s+reconciled\\b"
                    + "|\\b(?:account|balance|everything|it|this)\\s+reconciles\\b"
                    + "|\\breconciles\\s+perfectly\\b");
    private static final Pattern NEGATED_RECONCILED_CLAIM = Pattern.compile(
            "(?i)\\b(?:not|isn't|wasn't|cannot|can't|unable to|not able to)\\s+reconcile(?:d)?\\b|\\bnot\\s+reconciled\\b");
    private static final Map<String, String> MONTH_NUMBERS = Map.ofEntries(
            Map.entry("january", "01"),
            Map.entry("february", "02"),
            Map.entry("march", "03"),
            Map.entry("april", "04"),
            Map.entry("may", "05"),
            Map.entry("june", "06"),
            Map.entry("july", "07"),
            Map.entry("august", "08"),
            Map.entry("september", "09"),
            Map.entry("october", "10"),
            Map.entry("november", "11"),
            Map.entry("december", "12"));
    private static final String SPENDING_DELTA_EXPLAINER_TOOL = "spending_delta_explainer";
    private static final String TOP_MERCHANTS_TOOL = "top_merchants";
    private static final String SPEND_BY_CATEGORY_TOOL = "spend_by_category";
    private static final String SPEND_BY_MERCHANT_GROUP_TOOL = "spend_by_merchant_group";
    private static final String RECENT_TRANSACTIONS_TOOL = "recent_transactions";
    private static final String COMPARE_MONTHS_TOOL = "compare_months";

    private static final Pattern TOP_MERCHANT_REQUEST = Pattern.compile(
            "(?i)\\b(merchant|merchants|store|stores|vendor|vendors|shop|shops)\\b|\\bwhat merchant\\b|\\bwhich merchant\\b|\\btop merchant\\b|\\bwhich store\\b|\\bspend most on merchant\\b");

    private static final Pattern AT_MERCHANT_REQUEST = Pattern.compile(
            "(?i)\\b(?:at|from)\\s+([a-z0-9 &-]{2,30}?)(?=\\s+this|\\s+last|\\s+in|\\s+for|\\b)|\\bspend at\\s+([a-z0-9 &-]{2,30}?)(?=\\s+this|\\s+last|\\s+in|\\s+for|\\b)");

    private static final Pattern CATEGORY_SPEND_REQUEST = Pattern.compile(
            "(?i)\\bhow much\\b.*\\b(spend|spent|spending|expense|expenses)\\b|\\b(spend|spent|spending|expenses?)\\b.*\\b(this month|current month|present month|last month|previous month|month|mtd)\\b|\\bwhere did (?:most of )?(?:my|the) money (?:go|went)\\b|\\bspending (?:breakdown|summary|overview)\\b|\\bwhat did i spend\\b|\\bexpenses this month\\b|\\bgive me my spending breakdown\\b|\\bshow my (?:spending|expenses)\\b");

    private static final Pattern RECENT_TX_REQUEST = Pattern.compile(
            "(?i)(\\b(show|list|get|view|display)\\b.*\\b(recent|latest|last)\\b.*\\b(transaction|transactions|purchase|purchases|expense|expenses)\\b|\\brecent transactions\\b|\\blast transaction\\b|\\blast expense\\b|\\blast purchase\\b)");

    private static final Pattern COMPARE_MONTHS_REQUEST = Pattern.compile(
            "(?i)\\b(compare|comparison)\\b.*\\b(month|months|period|periods)\\b|\\bcompare this month with last month\\b");

    private final ChatModelPort chatModelPort;
    private final ToolRegistry toolRegistry;
    private final VectorSearchPort vectorSearchPort;
    private final EmbeddingPort embeddingPort;
    private final TokenBudgetManager tokenBudgetManager;
    private final CitationValidatorService citationValidatorService;
    private final NumericConsistencyValidator numericConsistencyValidator;
    private final FinancialQueryDetector financialQueryDetector;
    private final IntentClassifier intentClassifier;
    private final ConversationalQueryResolver conversationalQueryResolver;
    private final ChatAuditPersistenceService auditPersistenceService;
    private final ChatSessionStateRepository chatSessionStateRepository;
    private final ObjectMapper objectMapper;
    private final com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort financialAnalyticsPort;
    private final com.finsight.finsight_ai.repository.TransactionRepository transactionRepository;
    private final java.time.Clock clock;
    private final com.finsight.finsight_ai.ai.chat.application.plan.FinanceQueryPlanExecutor planExecutor;

    public ChatOrchestrationService(ChatModelPort chatModelPort,
                                    ToolRegistry toolRegistry,
                                    VectorSearchPort vectorSearchPort,
                                    EmbeddingPort embeddingPort,
                                    TokenBudgetManager tokenBudgetManager,
                                    CitationValidatorService citationValidatorService,
                                    NumericConsistencyValidator numericConsistencyValidator,
                                    FinancialQueryDetector financialQueryDetector,
                                    IntentClassifier intentClassifier,
                                    ChatAuditPersistenceService auditPersistenceService,
                                    ChatSessionStateRepository chatSessionStateRepository,
                                    ObjectMapper objectMapper) {
        this(chatModelPort, toolRegistry, vectorSearchPort, embeddingPort, tokenBudgetManager,
                citationValidatorService, numericConsistencyValidator, financialQueryDetector,
                intentClassifier, auditPersistenceService, chatSessionStateRepository, objectMapper,
                null, null);
    }

    public ChatOrchestrationService(ChatModelPort chatModelPort,
                                    ToolRegistry toolRegistry,
                                    VectorSearchPort vectorSearchPort,
                                    EmbeddingPort embeddingPort,
                                    TokenBudgetManager tokenBudgetManager,
                                    CitationValidatorService citationValidatorService,
                                    NumericConsistencyValidator numericConsistencyValidator,
                                    FinancialQueryDetector financialQueryDetector,
                                    IntentClassifier intentClassifier,
                                    ChatAuditPersistenceService auditPersistenceService,
                                    ChatSessionStateRepository chatSessionStateRepository,
                                    ObjectMapper objectMapper,
                                    com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort financialAnalyticsPort,
                                    com.finsight.finsight_ai.repository.TransactionRepository transactionRepository) {
        this(chatModelPort, toolRegistry, vectorSearchPort, embeddingPort, tokenBudgetManager,
                citationValidatorService, numericConsistencyValidator, financialQueryDetector,
                intentClassifier, new ConversationalQueryResolver(java.time.Clock.systemDefaultZone(), objectMapper),
                auditPersistenceService, chatSessionStateRepository, objectMapper,
                financialAnalyticsPort, transactionRepository, java.time.Clock.systemDefaultZone());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChatOrchestrationService(ChatModelPort chatModelPort,
                                    ToolRegistry toolRegistry,
                                    VectorSearchPort vectorSearchPort,
                                    EmbeddingPort embeddingPort,
                                    TokenBudgetManager tokenBudgetManager,
                                    CitationValidatorService citationValidatorService,
                                    NumericConsistencyValidator numericConsistencyValidator,
                                    FinancialQueryDetector financialQueryDetector,
                                    IntentClassifier intentClassifier,
                                    ConversationalQueryResolver conversationalQueryResolver,
                                    ChatAuditPersistenceService auditPersistenceService,
                                    ChatSessionStateRepository chatSessionStateRepository,
                                    ObjectMapper objectMapper,
                                    com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort financialAnalyticsPort,
                                    com.finsight.finsight_ai.repository.TransactionRepository transactionRepository) {
        this(chatModelPort, toolRegistry, vectorSearchPort, embeddingPort, tokenBudgetManager,
                citationValidatorService, numericConsistencyValidator, financialQueryDetector,
                intentClassifier, conversationalQueryResolver, auditPersistenceService,
                chatSessionStateRepository, objectMapper, financialAnalyticsPort,
                transactionRepository, java.time.Clock.systemDefaultZone());
    }

    public ChatOrchestrationService(ChatModelPort chatModelPort,
                                    ToolRegistry toolRegistry,
                                    VectorSearchPort vectorSearchPort,
                                    EmbeddingPort embeddingPort,
                                    TokenBudgetManager tokenBudgetManager,
                                    CitationValidatorService citationValidatorService,
                                    NumericConsistencyValidator numericConsistencyValidator,
                                    FinancialQueryDetector financialQueryDetector,
                                    IntentClassifier intentClassifier,
                                    ConversationalQueryResolver conversationalQueryResolver,
                                    ChatAuditPersistenceService auditPersistenceService,
                                    ChatSessionStateRepository chatSessionStateRepository,
                                    ObjectMapper objectMapper,
                                    com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort financialAnalyticsPort,
                                    com.finsight.finsight_ai.repository.TransactionRepository transactionRepository,
                                    java.time.Clock clock) {
        this.chatModelPort = chatModelPort;
        this.toolRegistry = toolRegistry;
        this.vectorSearchPort = vectorSearchPort;
        this.embeddingPort = embeddingPort;
        this.tokenBudgetManager = tokenBudgetManager;
        this.citationValidatorService = citationValidatorService;
        this.numericConsistencyValidator = numericConsistencyValidator;
        this.financialQueryDetector = financialQueryDetector;
        this.intentClassifier = intentClassifier;
        this.conversationalQueryResolver = conversationalQueryResolver;
        this.auditPersistenceService = auditPersistenceService;
        this.chatSessionStateRepository = chatSessionStateRepository;
        this.objectMapper = objectMapper;
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.planExecutor = new com.finsight.finsight_ai.ai.chat.application.plan.FinanceQueryPlanExecutor(financialAnalyticsPort, clock);
    }

    @Override
    public ChatResponse processChat(UUID userId, ChatRequest request) {
        if (userId == null || request == null || request.message() == null || request.message().isBlank()) {
            return new ChatResponse(UUID.randomUUID(), "Please provide a valid question.", List.of(), null);
        }
        TenantContext.set(userId);
        UserPromptContext.set(request.message());
        try {
            UUID conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID();
            ExecutionState state = new ExecutionState(userId, conversationId, request.message());
            return execute(state, request);
        } finally {
            UserPromptContext.clear();
            TenantContext.clear();
        }
    }

    private ChatResponse execute(ExecutionState state, ChatRequest request) {
        Optional<ChatSessionStateEntity> session = loadSession(state);
        ConversationalQueryResolver.MergedQuery merged = conversationalQueryResolver.mergeContext(
                request.message(), session.orElse(null));

        String rawClean = request.message().trim().toLowerCase(Locale.ROOT);
        if (rawClean.equals("good vs bad") || rawClean.equals("good vs. bad")) {
            String answer = "Sure! Would you like to compare:\n• income vs spending\n• this month vs last month\n• spending categories?";
            DialogueState newState = new DialogueState(
                    "MONTH_COMPARISON", null, null, java.time.YearMonth.now().toString(),
                    java.time.YearMonth.now().minusMonths(1).toString(), java.time.YearMonth.now().toString(),
                    null, null, null, null, null, "COMPARISON_TYPE", "CHOICE", "CLARIFICATION",
                    answer, Map.of()
            );
            saveSessionBestEffort(state, session, request.message(), answer, newState);
            TokenUsageMetaData meta = new TokenUsageMetaData(0, 0, 0, false, false);
            return new ChatResponse(state.conversationId, answer, List.of(), meta);
        }

        if ((rawClean.contains("spend by category comparison") || rawClean.contains("category spending comparison")) && merged.resolvedMonth() == null) {
            String answer = "Sure — which month would you like to compare? You can say something like 'August 2025' or 'last month'.";
            DialogueState newState = new DialogueState(
                    "CATEGORY_COMPARISON", "MONTH_OVER_MONTH", "CATEGORY", null,
                    null, null, null, null, null, "compare_months", null, "PERIOD", "PERIOD", "CLARIFICATION",
                    answer, Map.of()
            );
            saveSessionBestEffort(state, session, request.message(), answer, newState);
            TokenUsageMetaData meta = new TokenUsageMetaData(0, 0, 0, false, false);
            return new ChatResponse(state.conversationId, answer, List.of(), meta);
        }

        // Clarification prompt generated by ConversationalQueryResolver
        if (merged.normalizedMessage().startsWith("__CLARIFICATION__:")) {
            String answer = merged.normalizedMessage().substring("__CLARIFICATION__:".length());
            saveSessionBestEffort(state, session, request.message(), answer, merged.dialogueState());
            TokenUsageMetaData meta = new TokenUsageMetaData(0, 0, 0, false, false);
            return new ChatResponse(state.conversationId, answer, List.of(), meta);
        }

        // Natural continuation: user said yes/affirmative with no active pending action.
        // Return a short prompt without resurrecting stale dialogue state.
        if ("__NATURAL_CONTINUATION__".equals(merged.normalizedMessage())) {
            String answer = "What would you like to explore next?";
            saveSessionBestEffort(state, session, request.message(), answer, DialogueState.answered());
            TokenUsageMetaData meta = new TokenUsageMetaData(0, 0, 0, false, false);
            return new ChatResponse(state.conversationId, answer, List.of(), meta);
        }

        String queryToProcess = merged.normalizedMessage();
        IntentBucket intent = intentClassifier.classify(queryToProcess);
        if (intent == IntentBucket.GENERAL && (merged.inheritedToolName() != null 
                || (merged.dialogueState() != null && "CATEGORY_SPENDING".equals(merged.dialogueState().activeIntent()))
                || CATEGORY_SPEND_REQUEST.matcher(queryToProcess).find())) {
            intent = IntentBucket.AGGREGATE;
        }

        boolean requiresToolEvidence = financialQueryDetector.requiresToolEvidence(queryToProcess)
                || merged.inheritedToolName() != null;

        if (state.failed()) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }

        List<ChatTurn> history = buildHistory(session, queryToProcess);
        List<ToolSpec> availableTools = (intent == IntentBucket.GENERAL) ? List.of() : toolRegistry.getToolSpecs();
        ChatModelOutput output = generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "INITIAL_MODEL");
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceEntityComparisonWhenRequested(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceLowestCategoryWhenRequested(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceLowestTransactionWhenRequested(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceExplainFailureWhenRequested(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceBalanceReconcilerWhenModelMissesReconciliationTool(state, history, output, availableTools);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceSpendingDeltaExplainerWhenModelMissesDeltaTool(state, history, output, availableTools);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceTopMerchantsWhenModelMisses(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceSpendByCategoryWhenModelMisses(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceSpendByMerchantGroupWhenModelMisses(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceCashflowSummaryWhenModelMisses(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceRecentTransactionsWhenModelMisses(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }
        output = forceCompareMonthsWhenModelMisses(state, history, output, availableTools, merged);
        if (output == null) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }

        List<VectorSearchPort.ScoredTransaction> retrieved = retrieveIfNeeded(state, request.message());
        if (state.failed()) {
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }

        while (output.hasToolCalls() && state.toolTurns < MAX_TOOL_TURNS) {
            if (output.toolCalls().size() > MAX_TOOL_CALLS_PER_TURN) {
                state.fail("TOOL_LIMIT", "TOO_MANY_TOOL_CALLS");
                break;
            }

            state.toolTurns++;
            history.add(ChatTurn.assistant(output.textAnswer(), output.toolCalls()));
            List<ToolCallResult> toolResults = new ArrayList<>();

            for (ToolCallRequest toolCall : output.toolCalls()) {
                ToolExecutionResult execution = toolRegistry.execute(toolCall);
                toolResults.add(new ToolCallResult(
                        toolCall.callId(), toolCall.toolName(), execution.responseJson()));
                state.recordTool(toolCall, execution, "MODEL");
                if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
                    state.fail("TOOL", execution.errorCode());
                    break;
                }
            }
            history.add(ChatTurn.toolResults(toolResults));
            if (state.failed()) {
                break;
            }

            addRetrievedContext(state, history, retrieved);
            output = generate(state,
                    new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                    "CONTINUATION_MODEL");
            if (output == null) {
                break;
            }
        }

        if (output != null && output.hasToolCalls() && state.toolTurns >= MAX_TOOL_TURNS) {
            state.fail("TOOL_LIMIT", "TOOL_ROUND_LIMIT_EXCEEDED");
        }

        if (state.failed() || (requiresToolEvidence && !state.successfulToolEvidence)) {
            if (!state.failed()) {
                state.fail("EVIDENCE_GATE", "FINANCIAL_TOOL_EVIDENCE_REQUIRED");
            }
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }

        String candidate = output == null ? null : output.textAnswer();
        if (candidate == null || candidate.isBlank()) {
            state.fail("FINAL_ANSWER", "EMPTY_MODEL_RESPONSE");
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true, null, null);
            return fallback(state);
        }

        ValidationBundle validation = validate(candidate, state);
        if (!validation.valid()) {
            history.add(new ChatTurn(Role.ASSISTANT, candidate));
            history.add(new ChatTurn(Role.SYSTEM, correctionInstruction(validation)));
            ChatModelOutput corrected = generate(state,
                    new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, List.of()),
                    "VALIDATION_CORRECTION_MODEL");
            if (corrected == null || corrected.hasToolCalls()
                    || corrected.textAnswer() == null || corrected.textAnswer().isBlank()) {
                state.fail("VALIDATION", "VALIDATION_CORRECTION_FAILED");
                persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true,
                        validation.numeric(), validation.citation());
                return fallback(state);
            }
            candidate = corrected.textAnswer();
            validation = validate(candidate, state);
        }

        if (!validation.valid()) {
            state.fail("VALIDATION", "UNSUPPORTED_MODEL_CLAIMS");
            persistAudit(state, SAFE_UNVERIFIED_RESPONSE, true,
                    validation.numeric(), validation.citation());
            return fallback(state);
        }

        String finalAnswer = validation.citation().cleanedText();
        persistAudit(state, finalAnswer, false, validation.numeric(), validation.citation());
        // CRITICAL: persist an ANSWER state to clear any stale CLARIFICATION from DB
        saveSessionBestEffort(state, session, request.message(), finalAnswer, merged.dialogueState() != null ? merged.dialogueState().withAnswerState() : DialogueState.answered());


        List<TransactionCitation> citations = validation.citation().validCitations().stream()
                .map(id -> new TransactionCitation(id, null, null, null, null))
                .toList();
        return response(state, finalAnswer, citations);
    }

    private ChatModelOutput forceBalanceReconcilerWhenModelMissesReconciliationTool(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools
    ) {
        if (state.failed() || output.hasToolCalls() || !isReconciliationRequest(state.userMessage)) {
            return output;
        }
        boolean balanceToolAvailable = availableTools.stream()
                .anyMatch(tool -> BALANCE_RECONCILER_TOOL.equals(tool.name()));
        if (!balanceToolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                BALANCE_RECONCILER_TOOL,
                balanceReconcilerArguments(state.userMessage));
        state.recordRecovery(BALANCE_RECONCILER_TOOL, toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceSpendingDeltaExplainerWhenModelMissesDeltaTool(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools
    ) {
        if (state.failed() || output.hasToolCalls() || !isSpendingDeltaRequest(state.userMessage)) {
            return output;
        }
        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> SPENDING_DELTA_EXPLAINER_TOOL.equals(tool.name()));
        Map<String, Object> arguments = spendingDeltaArguments(state.userMessage);
        if (!toolAvailable || arguments.isEmpty() || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                SPENDING_DELTA_EXPLAINER_TOOL,
                arguments);
        state.recordRecovery(SPENDING_DELTA_EXPLAINER_TOOL, arguments);
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceTopMerchantsWhenModelMisses(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        boolean matches = TOP_MERCHANT_REQUEST.matcher(mergedQuery.normalizedMessage()).find()
                || TOP_MERCHANTS_TOOL.equals(mergedQuery.inheritedToolName());
        if (state.failed() || output.hasToolCalls() || !matches) {
            return output;
        }
        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> TOP_MERCHANTS_TOOL.equals(tool.name()));
        if (!toolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("limit", 5);
        if (mergedQuery.resolvedMonth() != null) {
            args.put("month", mergedQuery.resolvedMonth());
        }

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                TOP_MERCHANTS_TOOL,
                args);
        state.recordRecovery(TOP_MERCHANTS_TOOL, toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceSpendByCategoryWhenModelMisses(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed()) {
            return output;
        }

        boolean isDeltaQuery = mergedQuery.normalizedMessage().toLowerCase(Locale.ROOT).contains("increase")
                || mergedQuery.normalizedMessage().toLowerCase(Locale.ROOT).contains("decrease")
                || mergedQuery.normalizedMessage().toLowerCase(Locale.ROOT).contains("why did");

        boolean isCategoryIntent = !isDeltaQuery && (SPEND_BY_CATEGORY_TOOL.equals(mergedQuery.inheritedToolName())
                || (mergedQuery.dialogueState() != null && "CATEGORY_SPENDING".equals(mergedQuery.dialogueState().activeIntent()))
                || CATEGORY_SPEND_REQUEST.matcher(mergedQuery.normalizedMessage()).find());

        boolean outputCalledCategoryTool = output != null && output.hasToolCalls()
                && output.toolCalls().stream().anyMatch(tc -> SPEND_BY_CATEGORY_TOOL.equals(tc.toolName())
                        && tc.arguments() != null && tc.arguments().get("category") != null
                        && !tc.arguments().get("category").toString().isBlank());

        if (!isCategoryIntent || outputCalledCategoryTool) {
            return output;
        }

        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> SPEND_BY_CATEGORY_TOOL.equals(tool.name()));
        if (!toolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        String category = mergedQuery.inheritedCategory();
        if (category == null || category.isBlank() || category.toLowerCase(Locale.ROOT).contains("spending") || category.toLowerCase(Locale.ROOT).contains("month")) {
            category = "all";
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("category", category);
        String month = mergedQuery.resolvedMonth() != null ? mergedQuery.resolvedMonth() : java.time.YearMonth.now().toString();
        args.put("month", month);

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                SPEND_BY_CATEGORY_TOOL,
                args);
        state.recordRecovery(SPEND_BY_CATEGORY_TOOL, toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceSpendByMerchantGroupWhenModelMisses(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed() || output.hasToolCalls()) {
            return output;
        }
        var matcher = AT_MERCHANT_REQUEST.matcher(mergedQuery.normalizedMessage());
        if (!matcher.find()) {
            return output;
        }
        String merchant = matcher.group(1) != null ? matcher.group(1).trim() : (matcher.group(2) != null ? matcher.group(2).trim() : null);
        if (merchant == null || merchant.isBlank()) {
            return output;
        }
        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> SPEND_BY_MERCHANT_GROUP_TOOL.equals(tool.name()));
        if (!toolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("merchantGroup", merchant);
        if (mergedQuery.resolvedMonth() != null) {
            args.put("month", mergedQuery.resolvedMonth());
        }

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                SPEND_BY_MERCHANT_GROUP_TOOL,
                args);
        state.recordRecovery(SPEND_BY_MERCHANT_GROUP_TOOL, toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceCashflowSummaryWhenModelMisses(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed() || output.hasToolCalls()) {
            return output;
        }
        String lowerMsg = mergedQuery.normalizedMessage().toLowerCase(Locale.ROOT);
        boolean matches = lowerMsg.contains("cashflow")
                || lowerMsg.contains("income vs spend")
                || lowerMsg.contains("income and spend")
                || lowerMsg.contains("income vs spending")
                || "cashflow_summary".equals(mergedQuery.inheritedToolName());
        if (!matches) {
            return output;
        }
        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> "cashflow_summary".equals(tool.name()));
        if (!toolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        Map<String, Object> args = new LinkedHashMap<>();
        String month = mergedQuery.resolvedMonth() != null ? mergedQuery.resolvedMonth() : java.time.YearMonth.now().toString();
        args.put("month", month);

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                "cashflow_summary",
                args);
        state.recordRecovery("cashflow_summary", toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceRecentTransactionsWhenModelMisses(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        boolean matches = RECENT_TX_REQUEST.matcher(state.userMessage).find()
                || RECENT_TRANSACTIONS_TOOL.equals(mergedQuery.inheritedToolName());
        if (state.failed() || output.hasToolCalls() || !matches) {
            return output;
        }
        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> RECENT_TRANSACTIONS_TOOL.equals(tool.name()));
        if (!toolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                RECENT_TRANSACTIONS_TOOL,
                Map.of("limit", 5));
        state.recordRecovery(RECENT_TRANSACTIONS_TOOL, toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private ChatModelOutput forceCompareMonthsWhenModelMisses(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        boolean matches = COMPARE_MONTHS_REQUEST.matcher(mergedQuery.normalizedMessage()).find()
                || COMPARE_MONTHS_TOOL.equals(mergedQuery.inheritedToolName());
        if (state.failed() || output.hasToolCalls() || !matches) {
            return output;
        }
        boolean toolAvailable = availableTools.stream()
                .anyMatch(tool -> COMPARE_MONTHS_TOOL.equals(tool.name()));
        if (!toolAvailable || state.toolTurns >= MAX_TOOL_TURNS) {
            return output;
        }

        LocalDate now = LocalDate.now();
        String period2 = mergedQuery.resolvedMonth() != null ? mergedQuery.resolvedMonth() : String.format("%04d-%02d", now.getYear(), now.getMonthValue());
        LocalDate lastMonthDate = now.minusMonths(1);
        String period1 = String.format("%04d-%02d", lastMonthDate.getYear(), lastMonthDate.getMonthValue());

        if (mergedQuery.dialogueState() != null) {
            if (mergedQuery.dialogueState().periodA() != null) period1 = mergedQuery.dialogueState().periodA();
            if (mergedQuery.dialogueState().periodB() != null) period2 = mergedQuery.dialogueState().periodB();
        }

        state.toolTurns++;
        ToolCallRequest toolCall = new ToolCallRequest(
                "generated-" + UUID.randomUUID(),
                COMPARE_MONTHS_TOOL,
                Map.of("month1", period1, "month2", period2));
        state.recordRecovery(COMPARE_MONTHS_TOOL, toolCall.arguments());
        history.add(ChatTurn.assistant(null, List.of(toolCall)));

        ToolExecutionResult execution = toolRegistry.execute(toolCall);
        state.recordTool(toolCall, execution, "DETERMINISTIC_RECOVERY");
        history.add(ChatTurn.toolResults(List.of(new ToolCallResult(
                toolCall.callId(), toolCall.toolName(), execution.responseJson()))));
        if (execution.status() == ToolExecutionResult.Status.SYSTEM_ERROR) {
            state.fail("TOOL", execution.errorCode());
            return output;
        }

        return generate(state,
                new ChatModelInput(DEFAULT_SYSTEM_PROMPT, history, null, availableTools),
                "CONTINUATION_MODEL");
    }

    private boolean isReconciliationRequest(String message) {
        return message != null && RECONCILIATION_REQUEST.matcher(message).find();
    }

    private Map<String, Object> balanceReconcilerArguments(String message) {
        BigDecimal startingBalance = explicitStartingBalance(message);
        if (startingBalance == null) {
            return Map.of();
        }
        return Map.of("startingBalance", startingBalance);
    }

    private BigDecimal explicitStartingBalance(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = EXPLICIT_STARTING_BALANCE.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(2).replace(",", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isSpendingDeltaRequest(String message) {
        return message != null && SPENDING_DELTA_REQUEST.matcher(message).find();
    }

    private Map<String, Object> spendingDeltaArguments(String message) {
        List<String> months = monthYearValues(message);
        String categoryOrGroup = categoryBeforeSpending(message);
        if (months.size() < 2 || categoryOrGroup == null) {
            return Map.of();
        }
        return Map.of(
                "periodA", months.get(1),
                "periodB", months.get(0),
                "categoryOrGroup", categoryOrGroup);
    }

    private List<String> monthYearValues(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        java.util.regex.Matcher matcher = MONTH_YEAR.matcher(message);
        while (matcher.find()) {
            String month = MONTH_NUMBERS.get(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
            if (month != null) {
                values.add(matcher.group(2) + "-" + month);
            }
        }
        return values;
    }

    private String categoryBeforeSpending(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = CATEGORY_BEFORE_SPENDING.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private Optional<ChatSessionStateEntity> loadSession(ExecutionState state) {
        try {
            return chatSessionStateRepository.findByConversationIdAndUserId(
                    state.conversationId, state.userId);
        } catch (RuntimeException exception) {
            state.fail("SESSION_LOAD", "DATABASE_UNAVAILABLE");
            sanitizedFailureLog(state, "SESSION_LOAD", "DATABASE_UNAVAILABLE");
            return Optional.empty();
        }
    }

    private List<ChatTurn> buildHistory(Optional<ChatSessionStateEntity> session, String message) {
        List<ChatTurn> history = new ArrayList<>();
        session.ifPresent(value -> history.add(new ChatTurn(Role.SYSTEM, String.format(
                "Previous context - Last tool used: %s, Last tool params: %s, Last user message: %s, Last answer summary: %s",
                value.getLastToolName(), value.getLastToolParams(),
                value.getLastUserMessage(), value.getLastAnswerSummary()))));
        history.add(new ChatTurn(Role.USER, message));
        return history;
    }

    private ChatModelOutput generate(ExecutionState state, ChatModelInput input, String stage) {
        try {
            ChatModelOutput output = chatModelPort.generate(input);
            if (output == null) {
                state.fail(stage, "AI_PROVIDER_EMPTY_RESPONSE");
                return null;
            }
            state.promptTokens += output.promptTokens();
            state.completionTokens += output.completionTokens();
            return output;
        } catch (RuntimeException exception) {
            state.fail(stage, "AI_PROVIDER_UNAVAILABLE");
            sanitizedFailureLog(state, stage, "AI_PROVIDER_UNAVAILABLE");
            return null;
        }
    }

    private List<VectorSearchPort.ScoredTransaction> retrieveIfNeeded(ExecutionState state, String message) {
        if (message == null || !FUZZY_RETRIEVAL.matcher(message).find()) {
            return List.of();
        }
        try {
            float[] embedding = embeddingPort.embed(message);
            return vectorSearchPort.similaritySearch(state.userId, embedding, 20, 0.6);
        } catch (RuntimeException exception) {
            state.fail("RAG_RETRIEVAL", "AI_RETRIEVAL_UNAVAILABLE");
            sanitizedFailureLog(state, "RAG_RETRIEVAL", "AI_RETRIEVAL_UNAVAILABLE");
            return List.of();
        }
    }

    private void addRetrievedContext(ExecutionState state, List<ChatTurn> history,
                                     List<VectorSearchPort.ScoredTransaction> retrieved) {
        if (retrieved.isEmpty() || state.ragContextAdded) {
            return;
        }
        List<VectorSearchPort.ScoredTransaction> trimmed = tokenBudgetManager.trimToFit(
                retrieved, state.successfulToolOutputs, null);
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            String context = "Relevant transaction candidates for qualitative context only. "
                    + "Never calculate totals from this context; use sum_by_transaction_ids: "
                    + objectMapper.writeValueAsString(trimmed);
            history.add(new ChatTurn(Role.SYSTEM, context));
            trimmed.forEach(transaction -> state.transactionEvidenceIds.add(transaction.transactionId()));
            state.retrievedTransactionIds.addAll(
                    trimmed.stream().map(VectorSearchPort.ScoredTransaction::transactionId).toList());
            state.ragContextAdded = true;
        } catch (JsonProcessingException exception) {
            state.fail("RAG_CONTEXT", "RAG_CONTEXT_SERIALIZATION_FAILED");
        }
    }

    private ValidationBundle validate(String answer, ExecutionState state) {
        NumericConsistencyValidator.ValidationResult numeric =
                numericConsistencyValidator.validate(answer, state.numericEvidence);
        CitationValidatorService.CitationValidationResult citation =
                citationValidatorService.validate(answer, state.transactionEvidenceIds);
        List<String> semanticFailures = semanticConsistencyFailures(answer, state);
        state.lastSemanticFailures = semanticFailures;
        return new ValidationBundle(numeric, citation, semanticFailures);
    }

    private String correctionInstruction(ValidationBundle validation) {
        return "Validation failed. Rewrite the answer once using only the existing verified tool "
                + "results. Do not add new numbers or transaction identifiers. Unsupported numeric claims: "
                + validation.numeric().unsupportedClaims().size()
                + "; unsupported citations: " + validation.citation().invalidCitations().size()
                + "; semantic consistency failures: " + validation.semanticFailures().size() + ".";
    }

    private List<String> semanticConsistencyFailures(String answer, ExecutionState state) {
        if (!BALANCE_RECONCILER_TOOL.equals(state.lastToolName) || answer == null || answer.isBlank()) {
            return List.of();
        }
        String toolOutput = latestSuccessfulToolOutput(BALANCE_RECONCILER_TOOL, state);
        if (toolOutput == null) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(toolOutput);
            List<String> failures = new ArrayList<>();
            String normalized = answer.toLowerCase(java.util.Locale.ROOT);
            boolean reconciled = root.path("reconciled").asBoolean(false);
            if (!reconciled
                    && POSITIVE_RECONCILED_CLAIM.matcher(answer).find()
                    && !NEGATED_RECONCILED_CLAIM.matcher(answer).find()) {
                failures.add("BALANCE_RECONCILED_STATUS_CONTRADICTION");
            }
            if (reconciled && NEGATED_RECONCILED_CLAIM.matcher(answer).find()) {
                failures.add("BALANCE_RECONCILED_STATUS_CONTRADICTION");
            }
            BigDecimal totalExpense = decimalField(root, "totalExpense");
            if (totalExpense != null && totalExpense.signum() > 0
                    && (normalized.contains("no expenses") || normalized.contains("no expense"))) {
                failures.add("BALANCE_EXPENSE_CONTRADICTION");
            }
            return failures;
        } catch (RuntimeException | JsonProcessingException exception) {
            return List.of("BALANCE_STATUS_VALIDATION_UNAVAILABLE");
        }
    }

    private String latestSuccessfulToolOutput(String toolName, ExecutionState state) {
        for (int index = state.auditTrace.size() - 1; index >= 0; index--) {
            Map<String, Object> item = state.auditTrace.get(index);
            if ("TOOL".equals(item.get("stage"))
                    && toolName.equals(item.get("toolName"))
                    && "SUCCESS".equals(item.get("status"))) {
                int outputIndex = countSuccessfulToolsThrough(state.auditTrace, index) - 1;
                if (outputIndex >= 0 && outputIndex < state.successfulToolOutputs.size()) {
                    return state.successfulToolOutputs.get(outputIndex);
                }
            }
        }
        return null;
    }

    private int countSuccessfulToolsThrough(List<Map<String, Object>> trace, int endInclusive) {
        int count = 0;
        for (int index = 0; index <= endInclusive; index++) {
            Map<String, Object> item = trace.get(index);
            if ("TOOL".equals(item.get("stage")) && "SUCCESS".equals(item.get("status"))) {
                count++;
            }
        }
        return count;
    }

    private BigDecimal decimalField(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void persistAudit(ExecutionState state, String finalAnswer, boolean hallucinated,
                              NumericConsistencyValidator.ValidationResult numeric,
                              CitationValidatorService.CitationValidationResult citation) {
        ChatAuditLogEntity audit = new ChatAuditLogEntity();
        audit.setUserId(state.userId);
        audit.setConversationId(state.conversationId);
        audit.setUserMessage(state.userMessage);
        audit.setFinalAnswer(finalAnswer);
        audit.setPromptTokens(state.promptTokens);
        audit.setCompletionTokens(state.completionTokens);
        audit.setTotalTokens(state.promptTokens + state.completionTokens);
        audit.setFlaggedHallucination(hallucinated);
        audit.setToolTurns(state.toolTurns);

        List<Map<String, Object>> trace = new ArrayList<>(state.auditTrace);
        Map<String, Object> validationTrace = new LinkedHashMap<>();
        validationTrace.put("stage", "VALIDATION");
        validationTrace.put("numericStatus", numeric == null ? "NOT_RUN" : status(numeric.valid()));
        validationTrace.put("citationStatus", citation == null ? "NOT_RUN" : status(!citation.hallucinationDetected()));
        validationTrace.put("semanticStatus", numeric == null ? "NOT_RUN" : status(state.lastSemanticFailures.isEmpty()));
        validationTrace.put("semanticFailures", state.lastSemanticFailures);
        validationTrace.put("retrievedTransactionIds", state.retrievedTransactionIds);
        validationTrace.put("citedTransactionIds", citation == null ? List.of() : citation.validCitations());
        if (numeric != null) {
            validationTrace.put("unsupportedNumericClaims", numeric.unsupportedClaims().stream()
                    .map(claim -> claim.token())
                    .toList());
            validationTrace.put("numericGrounding", numeric.groundedClaims().stream()
                    .map(item -> Map.of(
                            "claim", item.claim().token(),
                            "tool", item.evidence().toolName(),
                            "field", item.evidence().field()))
                    .toList());
        }
        trace.add(validationTrace);
        try {
            audit.setToolCalls(objectMapper.writeValueAsString(trace));
        } catch (JsonProcessingException ignored) {
            audit.setToolCalls("[]");
        }
        if (hallucinated || state.failed()) {
            audit.setHallucinationDetails(state.failureCode == null
                    ? "MODEL_VALIDATION_FAILED"
                    : state.failureStage + ":" + state.failureCode);
        }

        try {
            auditPersistenceService.persist(audit);
        } catch (RuntimeException exception) {
            sanitizedFailureLog(state, "AUDIT_PERSIST", "AUDIT_PERSISTENCE_FAILED");
        }
    }

    private void saveSessionBestEffort(ExecutionState state,
                                       Optional<ChatSessionStateEntity> existing,
                                       String userMessage,
                                       String answer) {
        saveSessionBestEffort(state, existing, userMessage, answer, null);
    }

    private void saveSessionBestEffort(ExecutionState state,
                                       Optional<ChatSessionStateEntity> existing,
                                       String userMessage,
                                       String answer,
                                       DialogueState dialogueState) {
        try {
            ChatSessionStateEntity session = existing.orElseGet(ChatSessionStateEntity::new);
            session.setConversationId(state.conversationId);
            session.setUserId(state.userId);
            session.setLastUserMessage(userMessage);
            session.setLastAnswerSummary(answer.length() > 200
                    ? answer.substring(0, 200) + "..."
                    : answer);
            if (state.lastToolName != null) {
                session.setLastToolName(state.lastToolName);
                session.setLastToolParams(state.lastToolArgumentsJson);
            }
            if (dialogueState != null) {
                session.setDialogueState(objectMapper.writeValueAsString(dialogueState));
            }
            chatSessionStateRepository.save(session);
        } catch (Exception exception) {
            sanitizedFailureLog(state, "SESSION_PERSIST", "SESSION_PERSISTENCE_FAILED");
        }
    }

    private ChatModelOutput forceEntityComparisonWhenRequested(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed() || !"compare_entities".equals(mergedQuery.inheritedToolName())) {
            return output;
        }
        String msg = mergedQuery.normalizedMessage();
        String entityA = "coffee";
        String entityB = "pizza";
        if (mergedQuery.dialogueState() != null && mergedQuery.dialogueState().metadata() != null) {
            var slots = mergedQuery.dialogueState().metadata();
            if (slots.containsKey("entityA")) entityA = String.valueOf(slots.get("entityA"));
            if (slots.containsKey("entityB")) entityB = String.valueOf(slots.get("entityB"));
        } else {
            java.util.regex.Matcher matcher = Pattern.compile("(?i)compare\\s+(.+?)\\s+vs\\s+(.+?)(?:\\s+for|$)").matcher(msg);
            if (matcher.find()) {
                entityA = matcher.group(1).trim();
                entityB = matcher.group(2).trim();
            }
        }

        String monthStr = mergedQuery.resolvedMonth() != null ? mergedQuery.resolvedMonth() : java.time.YearMonth.now(clock).toString();

        com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan plan = new com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan(
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Operation.COMPARE,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Subject.SPENDING,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Dimension.CATEGORY,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Metric.SUM_AMOUNT,
                List.of(entityA, entityB),
                monthStr, null, null,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Order.NONE,
                null, null, null, "EXPENSE", false, null, 1.0
        );

        com.finsight.finsight_ai.ai.chat.application.plan.FinanceQueryPlanExecutor.ExecutionResult execRes = planExecutor.execute(state.userId, plan);

        if (execRes.success()) {
            ToolCallRequest req = new ToolCallRequest("gen-" + UUID.randomUUID(), execRes.toolName(), execRes.toolArguments());
            ToolExecutionResult exec = ToolExecutionResult.success("{}", execRes.numericEvidence(), execRes.transactionEvidenceIds());
            state.recordTool(req, exec, "DETERMINISTIC_RECOVERY");
            return new ChatModelOutput(execRes.textAnswer(), List.of(), 10, 10);
        }

        return output;
    }

    private ChatModelOutput forceLowestCategoryWhenRequested(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed() || !"lowest_category".equals(mergedQuery.inheritedToolName())) {
            return output;
        }

        String monthStr = mergedQuery.resolvedMonth() != null ? mergedQuery.resolvedMonth() : java.time.YearMonth.now(clock).toString();

        com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan plan = new com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan(
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Operation.RANK,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Subject.SPENDING,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Dimension.CATEGORY,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                monthStr, null, null,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Order.ASC,
                1, null, null, "EXPENSE", false, null, 1.0
        );

        com.finsight.finsight_ai.ai.chat.application.plan.FinanceQueryPlanExecutor.ExecutionResult execRes = planExecutor.execute(state.userId, plan);

        if (execRes.success()) {
            ToolCallRequest req = new ToolCallRequest("gen-" + UUID.randomUUID(), execRes.toolName(), execRes.toolArguments());
            ToolExecutionResult exec = ToolExecutionResult.success("{}", execRes.numericEvidence(), execRes.transactionEvidenceIds());
            state.recordTool(req, exec, "DETERMINISTIC_RECOVERY");
            return new ChatModelOutput(execRes.textAnswer(), List.of(), 10, 10);
        }

        return output;
    }

    private ChatModelOutput forceLowestTransactionWhenRequested(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed() || !"lowest_transaction".equals(mergedQuery.inheritedToolName())) {
            return output;
        }

        String monthStr = mergedQuery.resolvedMonth() != null ? mergedQuery.resolvedMonth() : java.time.YearMonth.now(clock).toString();

        com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan plan = new com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan(
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Operation.RANK,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Subject.TRANSACTION,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Dimension.TRANSACTION,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                monthStr, null, null,
                com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan.Order.ASC,
                1, null, null, "EXPENSE", false, null, 1.0
        );

        com.finsight.finsight_ai.ai.chat.application.plan.FinanceQueryPlanExecutor.ExecutionResult execRes = planExecutor.execute(state.userId, plan);

        if (execRes.success()) {
            ToolCallRequest req = new ToolCallRequest("gen-" + UUID.randomUUID(), execRes.toolName(), execRes.toolArguments());
            ToolExecutionResult exec = ToolExecutionResult.success("{}", execRes.numericEvidence(), execRes.transactionEvidenceIds());
            state.recordTool(req, exec, "DETERMINISTIC_RECOVERY");
            state.retrievedTransactionIds.addAll(execRes.transactionEvidenceIds());
            return new ChatModelOutput(execRes.textAnswer(), List.of(), 10, 10);
        }

        return output;
    }

    private ChatModelOutput forceExplainFailureWhenRequested(
            ExecutionState state,
            List<ChatTurn> history,
            ChatModelOutput output,
            List<ToolSpec> availableTools,
            ConversationalQueryResolver.MergedQuery mergedQuery
    ) {
        if (state.failed() || !"explain_failure".equals(mergedQuery.inheritedToolName())) {
            return output;
        }

        String answer = "The previous query could not be completed because matching financial data for that specific request was unavailable.";
        return new ChatModelOutput(answer, List.of(), 10, 10);
    }

    private ChatResponse fallback(ExecutionState state) {
        String answer;
        if ("NO_DATA".equals(state.failureCode)) {
            answer = "I don't see any spending transactions for this month.";
        } else if ("AI_PROVIDER_UNAVAILABLE".equals(state.failureCode) || "DATABASE_UNAVAILABLE".equals(state.failureCode)) {
            answer = "I couldn't retrieve your spending breakdown right now.";
        } else {
            answer = SAFE_UNVERIFIED_RESPONSE;
        }
        return new ChatResponse(
                state.conversationId,
                answer,
                List.of(),
                new TokenUsageMetaData(
                        state.promptTokens,
                        state.completionTokens,
                        state.promptTokens + state.completionTokens,
                        false,
                        false));
    }

    private ChatResponse response(ExecutionState state, String answer,
                                  List<TransactionCitation> citations) {
        boolean verifiedCalc = state.successfulToolEvidence && isVerifiedAnswerText(answer);
        return new ChatResponse(
                state.conversationId,
                answer,
                citations,
                new TokenUsageMetaData(
                        state.promptTokens,
                        state.completionTokens,
                        state.promptTokens + state.completionTokens,
                        state.successfulToolEvidence,
                        state.ragContextAdded,
                        verifiedCalc));
    }

    private static boolean isVerifiedAnswerText(String answer) {
        if (answer == null || answer.isBlank()) return false;
        if (SAFE_UNVERIFIED_RESPONSE.equals(answer)) return false;
        String lower = answer.toLowerCase(Locale.ROOT);
        return !lower.contains("i can't")
                && !lower.contains("i cannot")
                && !lower.contains("couldn't verify")
                && !lower.contains("could not verify")
                && !lower.contains("don't have access")
                && !lower.contains("do not have access")
                && !lower.contains("no access")
                && !lower.contains("please specify")
                && !lower.contains("please provide")
                && !lower.contains("what are you referring")
                && !lower.contains("what are you asking")
                && !lower.contains("i am sorry")
                && !lower.contains("i'm sorry");
    }

    private void sanitizedFailureLog(ExecutionState state, String stage, String code) {
        log.warn("Chat dependency failure userId={} conversationId={} stage={} code={}",
                state.userId, state.conversationId, stage, code);
    }

    private static String status(boolean valid) {
        return valid ? "PASSED" : "FAILED";
    }

    private record ValidationBundle(
            NumericConsistencyValidator.ValidationResult numeric,
            CitationValidatorService.CitationValidationResult citation,
            List<String> semanticFailures
    ) {
        private ValidationBundle {
            semanticFailures = semanticFailures == null ? List.of() : List.copyOf(semanticFailures);
        }

        boolean valid() {
            return numeric.valid() && !citation.hallucinationDetected() && semanticFailures.isEmpty();
        }
    }

    private final class ExecutionState {
        private final UUID userId;
        private final UUID conversationId;
        private final String userMessage;
        private final List<NumericEvidence> numericEvidence = new ArrayList<>();
        private final Set<UUID> transactionEvidenceIds = new LinkedHashSet<>();
        private final List<UUID> retrievedTransactionIds = new ArrayList<>();
        private final List<String> successfulToolOutputs = new ArrayList<>();
        private final List<Map<String, Object>> auditTrace = new ArrayList<>();
        private List<String> lastSemanticFailures = List.of();
        private int promptTokens;
        private int completionTokens;
        private int toolTurns;
        private boolean successfulToolEvidence;
        private boolean ragContextAdded;
        private String failureStage;
        private String failureCode;
        private String lastToolName;
        private String lastToolArgumentsJson;

        private ExecutionState(UUID userId, UUID conversationId, String userMessage) {
            this.userId = userId;
            this.conversationId = conversationId;
            this.userMessage = userMessage;
        }

        private void recordTool(ToolCallRequest call, ToolExecutionResult execution, String invocationSource) {
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("stage", "TOOL");
            audit.put("invocationSource", invocationSource);
            audit.put("callId", call.callId());
            audit.put("toolName", call.toolName());
            audit.put("arguments", call.arguments());
            audit.put("status", execution.status().name());
            audit.put("errorCode", execution.errorCode());
            auditTrace.add(audit);
            lastToolName = call.toolName();
            try {
                lastToolArgumentsJson = objectMapper.writeValueAsString(call.arguments());
            } catch (JsonProcessingException ignored) {
                lastToolArgumentsJson = "{}";
            }
            if (execution.successful()) {
                successfulToolEvidence = true;
                successfulToolOutputs.add(execution.responseJson());
                numericEvidence.addAll(execution.numericEvidence());
                transactionEvidenceIds.addAll(execution.transactionEvidenceIds());
            }
        }

        private void recordRecovery(String toolName, Map<String, Object> arguments) {
            auditTrace.add(Map.of(
                    "stage", "DETERMINISTIC_RECOVERY",
                    "modelToolRequested", false,
                    "recoveryTriggered", true,
                    "recoveryToolName", toolName,
                    "arguments", arguments));
        }

        private void fail(String stage, String code) {
            if (failureCode == null) {
                failureStage = stage;
                failureCode = code;
                auditTrace.add(Map.of(
                        "stage", stage,
                        "status", "SYSTEM_ERROR",
                        "errorCode", code));
            }
        }

        private boolean failed() {
            return failureCode != null;
        }
    }
}
