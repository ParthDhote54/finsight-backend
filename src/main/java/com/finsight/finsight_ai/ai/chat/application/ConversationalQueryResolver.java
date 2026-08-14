package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateEntity;
import com.finsight.finsight_ai.ai.chat.domain.DialogueState;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic conversational resolution layer:
 * 1. Lexical typo normalization.
 * 2. Temporal phrase resolution (this month → YYYY-MM, last month → YYYY-MM).
 *    Also resolves bare "current" → CURRENT_MONTH when a PERIOD slot is pending.
 * 3. Pending-slot resolution (fills PERIOD, CATEGORY_SCOPE, etc. from short replies).
 * 4. Multi-turn follow-up slot merging with session state.
 */
@Component
public class ConversationalQueryResolver {

    private final Clock clock;
    private final ObjectMapper objectMapper;

    private static final Pattern TYPO_WHERE = Pattern.compile("(?i)\\b(were|wer|wher)\\b");
    private static final Pattern TYPO_MONEY = Pattern.compile("(?i)\\b(mony|monyy|monie)\\b");
    private static final Pattern TYPO_THIS = Pattern.compile("(?i)\\b(dis)\\b");
    private static final Pattern TYPO_COMPARE = Pattern.compile("(?i)\\b(comapre|cmpare|compari|compr)\\b");
    private static final Pattern TYPO_SPENDING = Pattern.compile("(?i)\\b(spendign|spendingf|spendng|spendin|spendg|spendings|spends)\\b");
    private static final Pattern TYPO_EXPENSE = Pattern.compile("(?i)\\b(espense|expenss|expens)\\b");
    private static final Pattern TYPO_MERCHANT = Pattern.compile("(?i)\\b(merchnt|merchantt|merchants|merchnts)\\b");
    private static final Pattern TYPO_CATEGORY = Pattern.compile("(?i)\\b(categry|catgory|catagory|categor)\\b");
    private static final Pattern TYPO_SUBSCRIPTION = Pattern.compile("(?i)\\b(subscribtion|subscripton|subcription)\\b");

    private static final Pattern CURRENT_MONTH_PATTERN = Pattern.compile(
            "(?i)\\b(this month|current month|present month|month to date|mtd|this month's|this month only|for this month|spending for this month|current)\\b");
    // Bare "current" — only safe to resolve when a PERIOD slot is pending (handled in mergeContext)
    private static final Pattern BARE_CURRENT_PATTERN = Pattern.compile("(?i)^current$");
    private static final Pattern PREVIOUS_MONTH_PATTERN = Pattern.compile(
            "(?i)\\b(last month|previous month|prior month|past month|last month's|for last month|spending for last month)\\b");
    private static final Pattern EXPLICIT_MONTH_YEAR = Pattern.compile(
            "(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)\\s*(20\\d{2})?\\b");
    private static final Pattern YYYY_MM_PATTERN = Pattern.compile(
            "\\b(20\\d{2})-(0[1-9]|1[0-2])\\b");
    private static final Pattern SLASH_MONTH_YEAR = Pattern.compile(
            "\\b(0?[1-9]|1[0-2])[/-](20\\d{2})\\b");
    private static final Pattern YEAR_SLASH_MONTH = Pattern.compile(
            "\\b(20\\d{2})[/-](0?[1-9]|1[0-2])\\b");

    private static final Pattern AFFIRMATIVE_PATTERN = Pattern.compile(
            "(?i)^(yes|yep|yeah|yup|sure|okay|ok|please|do it|go ahead|sounds good|show me|tell me|absolutely|fine|why not)(\\.|!)?$");
    private static final Pattern NEGATIVE_PATTERN = Pattern.compile(
            "(?i)^(no|nope|nah|cancel|don't|not now|never mind|stop)(\\.|!)?$");

    private static final Pattern MERCHANT_INTENT = Pattern.compile(
            "(?i)\\b(merchant|merchants|store|stores|vendor|vendors|shop|shops|at swiggy|at amazon|spend at|spend on merchant|top merchant|which merchant|what merchant)\\b");

    /** Patterns that signal CATEGORY_SPENDING intent. */
    private static final Pattern CATEGORY_SPENDING_INTENT = Pattern.compile(
            "(?i)\\b(by category|by categories|spending breakdown|show breakdown|provide breakdown|show spending|all categories|compare categor|compare category|compare categories|category spending|category spend|spending by categor|where did.*money go|where money gone|where.*money go|where.*money gone|what did i spend|breakdown by|where did most of my money go|break down my spending|show spending breakdown|all spend category|show all.*category|compare.*category)\\b");

    private static final Map<String, String> MONTH_NAMES = Map.ofEntries(
            Map.entry("january", "01"), Map.entry("jan", "01"),
            Map.entry("february", "02"), Map.entry("feb", "02"),
            Map.entry("march", "03"), Map.entry("mar", "03"),
            Map.entry("april", "04"), Map.entry("apr", "04"),
            Map.entry("may", "05"),
            Map.entry("june", "06"), Map.entry("jun", "06"),
            Map.entry("july", "07"), Map.entry("jul", "07"),
            Map.entry("august", "08"), Map.entry("aug", "08"),
            Map.entry("september", "09"), Map.entry("sep", "09"),
            Map.entry("october", "10"), Map.entry("oct", "10"),
            Map.entry("november", "11"), Map.entry("nov", "11"),
            Map.entry("december", "12"), Map.entry("dec", "12")
    );

    private static final Pattern ENTITY_VS_ENTITY_PATTERN = Pattern.compile(
            "(?i)(?:i want my\\s+)?([a-z0-9 _-]+?)\\s+(?:spend|spending|expense|expenses)\\s+vs\\s+([a-z0-9 _-]+?)\\s+(?:spend|spending|expense|expenses)(?:\\s+for\\s+(this month|current month|last month|\\w+\\s+\\d{4}))?");

    private static final Pattern LOWEST_CATEGORY_PATTERN = Pattern.compile(
            "(?i)\\b(lowest|least|smallest|cheapest)\\s+(?:spending\\s+)?category\\b|\\bwhich category (?:had|has) (?:the )?(lowest|least|smallest|cheapest)\\s+spending\\b|\\blowest spending\\b");

    private static final Pattern LOWEST_TRANSACTION_PATTERN = Pattern.compile(
            "(?i)\\b(lowest|least|smallest|cheapest|min|minimum)\\s+(?:transaction|expense|purchase)\\b");

    private static final Pattern RECENT_TRANSACTIONS_PATTERN = Pattern.compile(
            "(?i)\\b(last|recent|latest)\\s+(?:transaction|transactions|expense|expenses|purchase|purchases)\\b");

    private static final Pattern OFF_DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b(prime minister|president|weather|capital of|who is|temperature|sports|movie|actor|actress|politics|trivia)\\b");

    private static final Pattern WHY_PATTERN = Pattern.compile(
            "(?i)^(why|why \\?\\?|why\\?|why so)$");

    @org.springframework.beans.factory.annotation.Autowired
    public ConversationalQueryResolver(ObjectMapper objectMapper) {
        this(Clock.systemDefaultZone(), objectMapper);
    }

    public ConversationalQueryResolver(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public String normalizeTypos(String text) {
        if (text == null || text.isBlank()) return text;
        String normalized = TYPO_WHERE.matcher(text).replaceAll("where");
        normalized = TYPO_MONEY.matcher(normalized).replaceAll("money");
        normalized = TYPO_THIS.matcher(normalized).replaceAll("this");
        normalized = TYPO_COMPARE.matcher(normalized).replaceAll("compare");
        normalized = TYPO_SPENDING.matcher(normalized).replaceAll("spending");
        normalized = TYPO_EXPENSE.matcher(normalized).replaceAll("expenses");
        normalized = TYPO_MERCHANT.matcher(normalized).replaceAll("merchant");
        normalized = TYPO_CATEGORY.matcher(normalized).replaceAll("category");
        normalized = TYPO_SUBSCRIPTION.matcher(normalized).replaceAll("subscription");
        return normalized;
    }

    public String resolveMonth(String text) {
        if (text == null || text.isBlank()) return null;

        Matcher isoMatcher = YYYY_MM_PATTERN.matcher(text);
        if (isoMatcher.find()) return isoMatcher.group(0);

        Matcher slashMatcher = SLASH_MONTH_YEAR.matcher(text);
        if (slashMatcher.find()) {
            int m = Integer.parseInt(slashMatcher.group(1));
            int y = Integer.parseInt(slashMatcher.group(2));
            return String.format("%04d-%02d", y, m);
        }

        Matcher yearSlashMatcher = YEAR_SLASH_MONTH.matcher(text);
        if (yearSlashMatcher.find()) {
            int y = Integer.parseInt(yearSlashMatcher.group(1));
            int m = Integer.parseInt(yearSlashMatcher.group(2));
            return String.format("%04d-%02d", y, m);
        }

        if (CURRENT_MONTH_PATTERN.matcher(text).find()) return YearMonth.now(clock).toString();
        if (PREVIOUS_MONTH_PATTERN.matcher(text).find()) return YearMonth.now(clock).minusMonths(1).toString();

        Matcher monthMatcher = EXPLICIT_MONTH_YEAR.matcher(text);
        if (monthMatcher.find()) {
            String monthStr = monthMatcher.group(1).toLowerCase(Locale.ROOT);
            String yearStr = monthMatcher.group(2);
            String monthNum = MONTH_NAMES.get(monthStr);
            if (monthNum != null) {
                int year = yearStr != null ? Integer.parseInt(yearStr) : YearMonth.now(clock).getYear();
                return String.format("%04d-%s", year, monthNum);
            }
        }

        return null;
    }

    public DialogueState parseDialogueState(ChatSessionStateEntity previousSession) {
        if (previousSession == null || previousSession.getDialogueState() == null) {
            return DialogueState.empty();
        }
        try {
            return objectMapper.readValue(previousSession.getDialogueState(), DialogueState.class);
        } catch (Exception e) {
            return DialogueState.empty();
        }
    }

    public record MergedQuery(
            String normalizedMessage,
            String resolvedMonth,
            String inheritedToolName,
            String inheritedCategory,
            String inheritedMerchantGroup,
            DialogueState dialogueState,
            boolean isAffirmative,
            boolean isNegative
    ) {
        public MergedQuery(String normalizedMessage, String resolvedMonth, String inheritedToolName, String inheritedCategory, String inheritedMerchantGroup) {
            this(normalizedMessage, resolvedMonth, inheritedToolName, inheritedCategory, inheritedMerchantGroup, DialogueState.empty(), false, false);
        }
    }

    public MergedQuery mergeContext(String userMessage, ChatSessionStateEntity previousSession) {
        String normalizedMessage = normalizeTypos(userMessage);
        String lowerMsg = normalizedMessage != null ? normalizedMessage.trim().toLowerCase(Locale.ROOT) : "";

        boolean isAffirmative = AFFIRMATIVE_PATTERN.matcher(userMessage != null ? userMessage.trim() : "").matches();
        boolean isNegative = NEGATIVE_PATTERN.matcher(userMessage != null ? userMessage.trim() : "").matches();

        DialogueState prevState = parseDialogueState(previousSession);

        // ── PRIORITY 1: PENDING SLOT RESOLUTION ──────────────────────────────────
        // Fill PERIOD slot from "current", "this month", or any month expression
        if ("PERIOD".equals(prevState.pendingSlot()) || "PERIOD".equals(prevState.expectedReplyType())) {
            String resolvedFromBare = null;
            if (BARE_CURRENT_PATTERN.matcher(lowerMsg).matches()) {
                resolvedFromBare = YearMonth.now(clock).toString();
            }
            String resolvedMonth = resolvedFromBare != null ? resolvedFromBare : resolveMonth(normalizedMessage);

            if (resolvedMonth != null) {
                String effectiveIntent = prevState.activeIntent() != null ? prevState.activeIntent() : "CATEGORY_SPENDING";
                String effectiveScope = prevState.categoryScope() != null ? prevState.categoryScope() : "ALL";
                normalizedMessage = "spending breakdown by category for " + resolvedMonth;
                DialogueState updatedState = new DialogueState(
                        effectiveIntent, prevState.comparisonMode(), prevState.metric(), resolvedMonth,
                        prevState.periodA(), prevState.periodB(), prevState.category(),
                        effectiveScope, prevState.merchantGroup(),
                        "spend_by_category", null, null, "NONE", "ANSWER", null, Map.of()
                );
                return new MergedQuery(normalizedMessage, resolvedMonth, "spend_by_category", null, null, updatedState, isAffirmative, isNegative);
            } else if (lowerMsg.equals("all") || lowerMsg.contains("all categories") || lowerMsg.contains("every category")) {
                // "all" provided while PERIOD is pending: retain CATEGORY_SPENDING and ask for period
                String answer = "Sure — I'll compare all spending categories. Which month should I use? You can say 'this month', 'last month', or 'August 2025'.";
                DialogueState updatedState = new DialogueState(
                        "CATEGORY_SPENDING", null, null, null,
                        null, null, null, "ALL", null,
                        null, null, "PERIOD", "PERIOD", "CLARIFICATION", answer, Map.of()
                );
                return new MergedQuery("__CLARIFICATION__:" + answer, null, null, null, null, updatedState, isAffirmative, isNegative);
            }
        }

        // Fill CATEGORY_SCOPE slot from "all" or similar
        if ("CATEGORY_SCOPE".equals(prevState.pendingSlot())) {
            if (lowerMsg.equals("all") || lowerMsg.contains("all categories") || lowerMsg.contains("every category")) {
                if (prevState.period() != null) {
                    normalizedMessage = "spending breakdown by category for " + prevState.period();
                    DialogueState updatedState = new DialogueState(
                            "CATEGORY_SPENDING", prevState.comparisonMode(), prevState.metric(), prevState.period(),
                            prevState.periodA(), prevState.periodB(), prevState.category(),
                            "ALL", prevState.merchantGroup(),
                            "spend_by_category", null, null, "NONE", "ANSWER", null, Map.of()
                    );
                    return new MergedQuery(normalizedMessage, prevState.period(), "spend_by_category", null, null, updatedState, isAffirmative, isNegative);
                } else {
                    String answer = "Sure — I'll compare all spending categories. Which month should I use? You can say 'this month', 'last month', or 'August 2025'.";
                    DialogueState updatedState = new DialogueState(
                            "CATEGORY_SPENDING", null, null, null,
                            null, null, null,
                            "ALL", null,
                            null, null, "PERIOD", "PERIOD", "CLARIFICATION", answer, Map.of()
                    );
                    return new MergedQuery("__CLARIFICATION__:" + answer, null, null, null, null, updatedState, isAffirmative, isNegative);
                }
            }
        }

        // ── OFF-DOMAIN REFUSAL ──
        if (OFF_DOMAIN_PATTERN.matcher(lowerMsg).find()) {
            String refusal = "I am FinSight AI, a conversational financial assistant. I can only answer questions related to your personal finances, spending, accounts, and budgets.";
            return new MergedQuery("__CLARIFICATION__:" + refusal, null, null, null, null, DialogueState.empty(), isAffirmative, isNegative);
        }

        // ── WHY CONTINUITY ──
        if (WHY_PATTERN.matcher(lowerMsg).matches()) {
            if (previousSession != null) {
                String lastUserMsg = previousSession.getLastUserMessage();
                if ("spend_by_category".equals(prevState.lastToolName()) || "spending_delta_explainer".equals(prevState.lastToolName())) {
                    return new MergedQuery("why did my spending change", prevState.period(), "spending_delta_explainer", null, null, prevState, isAffirmative, isNegative);
                } else {
                    String explanation = (lastUserMsg != null && !lastUserMsg.isBlank())
                            ? "The previous query ('" + lastUserMsg + "') could not be verified from your recorded financial transactions for that period."
                            : "The previous query could not be verified from your recorded financial transactions.";
                    return new MergedQuery("__CLARIFICATION__:" + explanation, prevState.period(), null, null, null, prevState, isAffirmative, isNegative);
                }
            } else {
                String explanation = "There is no previous context to explain. Please ask a financial query first.";
                return new MergedQuery("__CLARIFICATION__:" + explanation, null, null, null, null, DialogueState.empty(), isAffirmative, isNegative);
            }
        }

        // ── RECENT TRANSACTIONS ──
        if (RECENT_TRANSACTIONS_PATTERN.matcher(lowerMsg).find()) {
            String resolvedMonth = resolveMonth(normalizedMessage);
            if (resolvedMonth == null) {
                resolvedMonth = YearMonth.now(clock).toString();
            }
            DialogueState updatedState = new DialogueState(
                    "RECENT_TRANSACTIONS", null, null, resolvedMonth, null, null, null, null, null,
                    "recent_transactions", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery("recent transactions for " + resolvedMonth, resolvedMonth, "recent_transactions", null, null, updatedState, isAffirmative, isNegative);
        }

        // ── YEAR FOLLOW-UP RESOLUTION ──
        if (lowerMsg.equals("current year") || lowerMsg.equals("this year") || lowerMsg.matches("^20\\d{2}$")) {
            String effectiveMonth = (prevState != null && prevState.period() != null) ? prevState.period() : YearMonth.now(clock).toString();
            if (prevState != null && prevState.lastToolName() != null) {
                String tool = prevState.lastToolName();
                String normalizedFollowUp = switch (tool) {
                    case "lowest_transaction" -> "lowest transaction for " + effectiveMonth;
                    case "lowest_category" -> "lowest spending category for " + effectiveMonth;
                    case "recent_transactions" -> "recent transactions for " + effectiveMonth;
                    case "spend_by_category" -> "spending breakdown by category for " + effectiveMonth;
                    default -> normalizedMessage;
                };
                DialogueState updatedState = new DialogueState(
                        prevState.activeIntent(),
                        prevState.comparisonMode(),
                        prevState.metric(),
                        effectiveMonth,
                        prevState.periodA(),
                        prevState.periodB(),
                        prevState.category(),
                        prevState.categoryScope(),
                        prevState.merchantGroup(),
                        tool,
                        null,
                        null,
                        "NONE",
                        "ANSWER",
                        null,
                        prevState.metadata() == null ? Map.of() : prevState.metadata()
                );
                return new MergedQuery(
                        normalizedFollowUp,
                        effectiveMonth,
                        tool,
                        prevState.category(),
                        prevState.merchantGroup(),
                        updatedState,
                        isAffirmative,
                        isNegative
                );
            }

            DialogueState updatedState = new DialogueState(
                    "CATEGORY_SPENDING", null, null, effectiveMonth, null, null, null, "ALL", null,
                    "spend_by_category", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery(
                    "spending breakdown by category for " + effectiveMonth,
                    effectiveMonth,
                    "spend_by_category",
                    null,
                    null,
                    updatedState,
                    isAffirmative,
                    isNegative
            );
        }

        // ── ENTITY VS ENTITY ──
        Matcher entityMatcher = ENTITY_VS_ENTITY_PATTERN.matcher(lowerMsg);
        if (entityMatcher.find()) {
            String entityA = entityMatcher.group(1).trim().replaceAll("(?i)\\b(spending|spend|my)\\b", "").trim();
            String entityB = entityMatcher.group(2).trim().replaceAll("(?i)\\b(spending|spend|my)\\b", "").trim();
            String resolvedMonth = resolveMonth(normalizedMessage);
            if (resolvedMonth == null && (lowerMsg.contains("this month") || lowerMsg.contains("current month"))) {
                resolvedMonth = YearMonth.now(clock).toString();
            }
            if (resolvedMonth == null && prevState.period() != null) {
                resolvedMonth = prevState.period();
            }
            if (resolvedMonth == null) {
                resolvedMonth = YearMonth.now(clock).toString();
            }
            DialogueState updatedState = new DialogueState(
                    "COMPARE_ENTITIES", "VS", null, resolvedMonth, null, null, null, null, null,
                    "compare_entities", null, null, "NONE", "ANSWER", null, Map.of("entityA", entityA, "entityB", entityB)
            );
            return new MergedQuery("compare " + entityA + " vs " + entityB + " for " + resolvedMonth, resolvedMonth, "compare_entities", null, null, updatedState, isAffirmative, isNegative);
        }

        // ── LOWEST CATEGORY ──
        if (LOWEST_CATEGORY_PATTERN.matcher(lowerMsg).find()) {
            String resolvedMonth = resolveMonth(normalizedMessage);
            boolean hasMonthMention = lowerMsg.contains("this month") || lowerMsg.contains("current month") || lowerMsg.contains("last month");
            if (resolvedMonth == null && hasMonthMention) {
                resolvedMonth = YearMonth.now(clock).toString();
            }
            if (resolvedMonth == null && prevState.period() != null) {
                resolvedMonth = prevState.period();
            }
            if (resolvedMonth == null) {
                String answer = "I need a month to tell you which category had the lowest spending. Which month are you interested in?";
                DialogueState updatedState = new DialogueState(
                        "LOWEST_CATEGORY", null, "MIN_AMOUNT", null, null, null, null, "ALL", null,
                        null, null, "PERIOD", "PERIOD", "CLARIFICATION", answer, Map.of()
                );
                return new MergedQuery("__CLARIFICATION__:" + answer, null, null, null, null, updatedState, isAffirmative, isNegative);
            }
            DialogueState updatedState = new DialogueState(
                    "LOWEST_CATEGORY", null, "MIN_AMOUNT", resolvedMonth, null, null, null, "ALL", null,
                    "lowest_category", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery("lowest spending category for " + resolvedMonth, resolvedMonth, "lowest_category", null, null, updatedState, isAffirmative, isNegative);
        }

        // ── LOWEST TRANSACTION ──
        if (LOWEST_TRANSACTION_PATTERN.matcher(lowerMsg).find()) {
            String resolvedMonth = resolveMonth(normalizedMessage);
            if (resolvedMonth == null && (lowerMsg.contains("this month") || lowerMsg.contains("current month"))) {
                resolvedMonth = YearMonth.now(clock).toString();
            }
            if (resolvedMonth == null && prevState.period() != null) {
                resolvedMonth = prevState.period();
            }
            if (resolvedMonth == null) {
                resolvedMonth = YearMonth.now(clock).toString();
            }
            DialogueState updatedState = new DialogueState(
                    "LOWEST_TRANSACTION", null, "MIN_AMOUNT", resolvedMonth, null, null, null, null, null,
                    "lowest_transaction", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery("lowest transaction for " + resolvedMonth, resolvedMonth, "lowest_transaction", null, null, updatedState, isAffirmative, isNegative);
        }

        // ── PRIORITY 2: EXPLICIT CATEGORY_SPENDING INTENT ────────────────────────
        if (CATEGORY_SPENDING_INTENT.matcher(lowerMsg).find() 
                && !MERCHANT_INTENT.matcher(lowerMsg).find()
                && !lowerMsg.contains("increase") 
                && !lowerMsg.contains("decrease") 
                && !lowerMsg.contains("why did")) {
            String resolvedMonth = resolveMonth(normalizedMessage);
            if (resolvedMonth == null && prevState.period() != null) {
                resolvedMonth = prevState.period();
            }

            boolean isBareFragment = lowerMsg.equals("by category") || lowerMsg.equals("by categories");

            if (resolvedMonth == null && isBareFragment) {
                String answer = "Sure — I'll compare all spending categories. Which month should I use? You can say 'this month', 'last month', or 'August 2025'.";
                DialogueState updatedState = new DialogueState(
                        "CATEGORY_SPENDING", null, null, null,
                        null, null, null, "ALL", null,
                        null, null, "PERIOD", "PERIOD", "CLARIFICATION", answer, Map.of()
                );
                return new MergedQuery("__CLARIFICATION__:" + answer, null, null, null, null, updatedState, isAffirmative, isNegative);
            }

            if (resolvedMonth == null) {
                resolvedMonth = YearMonth.now(clock).toString();
            }

            normalizedMessage = "spending breakdown by category for " + resolvedMonth;
            DialogueState updatedState = new DialogueState(
                    "CATEGORY_SPENDING", null, null, resolvedMonth,
                    null, null, null, "ALL", null,
                    "spend_by_category", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery(normalizedMessage, resolvedMonth, "spend_by_category", null, null, updatedState, isAffirmative, isNegative);
        }

        // ── PRIORITY 3: ACTIVE CATEGORY_SPENDING INTENT — short follow-ups ──────
        if ("CATEGORY_SPENDING".equals(prevState.activeIntent())) {
            if (lowerMsg.equals("all") || lowerMsg.contains("all categories") || lowerMsg.contains("every category")) {
                if (prevState.period() != null) {
                    normalizedMessage = "spending breakdown by category for " + prevState.period();
                    DialogueState updatedState = new DialogueState(
                            "CATEGORY_SPENDING", null, null, prevState.period(),
                            null, null, null, "ALL", null,
                            "spend_by_category", null, null, "NONE", "ANSWER", null, Map.of()
                    );
                    return new MergedQuery(normalizedMessage, prevState.period(), "spend_by_category", null, null, updatedState, isAffirmative, isNegative);
                } else {
                    String answer = "Sure — I'll show all spending categories. Which month should I use? You can say 'this month', 'last month', or 'August 2025'.";
                    DialogueState updatedState = new DialogueState(
                            "CATEGORY_SPENDING", null, null, null,
                            null, null, null, "ALL", null,
                            null, null, "PERIOD", "PERIOD", "CLARIFICATION", answer, Map.of()
                    );
                    return new MergedQuery("__CLARIFICATION__:" + answer, null, null, null, null, updatedState, isAffirmative, isNegative);
                }
            }
        }

        // ── PERIOD resolve then attach month annotation ───────────────────────────
        String resolvedMonth = resolveMonth(normalizedMessage);
        if (resolvedMonth != null && !normalizedMessage.contains(resolvedMonth)) {
            normalizedMessage = normalizedMessage + " (period: " + resolvedMonth + ")";
        }

        if (previousSession == null) {
            return new MergedQuery(normalizedMessage, resolvedMonth, null, null, null, DialogueState.empty(), isAffirmative, isNegative);
        }

        // ── YES_NO AFFIRMATION (strict) ──────────────────────────────────────────
        if ("YES_NO".equals(prevState.expectedReplyType())
                && ("OFFER".equals(prevState.lastAssistantAct()) || "CONFIRMATION".equals(prevState.lastAssistantAct()))
                && prevState.pendingAction() != null
                && isAffirmative) {
            String pendingAction = prevState.pendingAction();
            DialogueState clearedState = new DialogueState(
                    prevState.activeIntent(), prevState.comparisonMode(), prevState.metric(), prevState.period(),
                    prevState.periodA(), prevState.periodB(), prevState.category(), prevState.categoryScope(),
                    prevState.merchantGroup(), prevState.lastToolName(), null, null, "NONE", "ANSWER", null, Map.of()
            );
            if ("SHOW_SPENDING_BY_CATEGORY".equals(pendingAction)) {
                String effectiveMonth = resolvedMonth != null ? resolvedMonth : (prevState.period() != null ? prevState.period() : YearMonth.now(clock).toString());
                normalizedMessage = "spending breakdown by category for " + effectiveMonth;
            } else if ("COMPARE_MONTHS".equals(pendingAction)) {
                String pA = prevState.periodA() != null ? prevState.periodA() : YearMonth.now(clock).minusMonths(1).toString();
                String pB = prevState.periodB() != null ? prevState.periodB() : YearMonth.now(clock).toString();
                normalizedMessage = "compare spending between " + pA + " and " + pB;
            } else if ("CASHFLOW_SUMMARY".equals(pendingAction)) {
                String effectiveMonth = resolvedMonth != null ? resolvedMonth : (prevState.period() != null ? prevState.period() : YearMonth.now(clock).toString());
                normalizedMessage = "show cashflow for " + effectiveMonth;
            }
            return new MergedQuery(normalizedMessage, resolvedMonth, prevState.lastToolName(), prevState.category(), prevState.merchantGroup(), clearedState, isAffirmative, isNegative);
        }

        // Affirmative with no active YES_NO → natural continuation
        if (isAffirmative && (prevState.pendingAction() == null || !"YES_NO".equals(prevState.expectedReplyType()))) {
            return new MergedQuery("__NATURAL_CONTINUATION__", resolvedMonth, null, null, null, DialogueState.answered(), isAffirmative, isNegative);
        }

        // Negation: clear state
        if ("YES_NO".equals(prevState.expectedReplyType()) && isNegative) {
            return new MergedQuery(normalizedMessage, resolvedMonth, null, null, null, DialogueState.answered(), isAffirmative, isNegative);
        }

        // ── PERIOD SLOT RESOLUTION ─────────────────────────────
        if ("LOWEST_CATEGORY".equals(prevState.activeIntent()) && "PERIOD".equals(prevState.expectedReplyType()) && resolvedMonth != null) {
            DialogueState updatedState = new DialogueState(
                    "LOWEST_CATEGORY", null, "MIN_AMOUNT", resolvedMonth, null, null, null, "ALL", null,
                    "lowest_category", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery("lowest spending category for " + resolvedMonth, resolvedMonth, "lowest_category", null, null, updatedState, isAffirmative, isNegative);
        }

        if ("CATEGORY_COMPARISON".equals(prevState.activeIntent()) && "PERIOD".equals(prevState.expectedReplyType()) && resolvedMonth != null) {
            String pB = resolvedMonth;
            String pA = YearMonth.parse(resolvedMonth).minusMonths(1).toString();
            normalizedMessage = "compare category spending between " + pA + " and " + pB;
            DialogueState updatedState = new DialogueState(
                    "CATEGORY_COMPARISON", "MONTH_OVER_MONTH", "CATEGORY", pB, pA, pB,
                    null, null, null, "compare_months", null, null, "NONE", "ANSWER", null, Map.of()
            );
            return new MergedQuery(normalizedMessage, resolvedMonth, "compare_months", null, null, updatedState, isAffirmative, isNegative);
        }

        // ── COMPARISON TYPE / METRIC FLOW ─────────────────────────────────────────
        if ("COMPARISON_TYPE".equals(prevState.pendingSlot()) || "CHOICE".equals(prevState.expectedReplyType())) {
            if (lowerMsg.contains("month over month") || lowerMsg.contains("month-on-month") || lowerMsg.equals("mom")) {
                normalizedMessage = "What would you like to compare month over month? Should I compare spending, or income vs spending?";
                DialogueState updatedState = new DialogueState(
                        "MONTH_COMPARISON", "MONTH_OVER_MONTH", null, YearMonth.now(clock).toString(),
                        YearMonth.now(clock).minusMonths(1).toString(), YearMonth.now(clock).toString(),
                        null, null, null, null, null, "METRIC", "METRIC", "CLARIFICATION",
                        "Should I compare spending, or income vs spending?", Map.of()
                );
                return new MergedQuery(normalizedMessage, resolvedMonth, null, null, null, updatedState, isAffirmative, isNegative);
            }
        }

        if ("METRIC".equals(prevState.pendingSlot()) || "METRIC".equals(prevState.expectedReplyType())) {
            if (lowerMsg.contains("income vs spend") || lowerMsg.contains("income and spend") || lowerMsg.contains("income vs spending") || lowerMsg.equals("both")) {
                String pA = prevState.periodA() != null ? prevState.periodA() : YearMonth.now(clock).minusMonths(1).toString();
                String pB = prevState.periodB() != null ? prevState.periodB() : YearMonth.now(clock).toString();
                normalizedMessage = "compare income vs spending between " + pA + " and " + pB;
                DialogueState updatedState = new DialogueState(
                        "CASHFLOW_COMPARISON", "MONTH_OVER_MONTH", "INCOME_VS_SPENDING", pB, pA, pB,
                        null, null, null, "cashflow_summary", null, null, "NONE", "ANSWER", null, Map.of()
                );
                return new MergedQuery(normalizedMessage, resolvedMonth, "cashflow_summary", null, null, updatedState, isAffirmative, isNegative);
            }
        }

        // ── SHORT FOLLOW-UP INHERITANCE ───────────────────────────────────────────
        String lastToolName = previousSession.getLastToolName();
        Map<String, Object> lastParams = parseParams(previousSession.getLastToolParams());

        boolean isShortFollowUp = isShortFollowUp(userMessage);

        if (isShortFollowUp && lastToolName != null) {
            String effectiveMonth = resolvedMonth != null ? resolvedMonth : (String) lastParams.get("month");
            String category = (String) lastParams.get("category");
            if (category == null) category = (String) lastParams.get("categoryOrGroup");
            String merchantGroup = (String) lastParams.get("merchantGroup");

            if (category != null && !category.isBlank()) {
                normalizedMessage = "How much did I spend on " + category + (effectiveMonth != null ? " in " + effectiveMonth : "");
            } else if ("top_merchants".equals(lastToolName)) {
                normalizedMessage = "What merchant did I spend most on" + (effectiveMonth != null ? " in " + effectiveMonth : "");
            }

            return new MergedQuery(normalizedMessage, effectiveMonth, lastToolName, category, merchantGroup, prevState, isAffirmative, isNegative);
        }

        return new MergedQuery(normalizedMessage, resolvedMonth, null, null, null, prevState, isAffirmative, isNegative);
    }

    private boolean isShortFollowUp(String text) {
        if (text == null) return false;
        String trimmed = text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.contains("spending") || trimmed.contains("expenses") || trimmed.contains("where did")
                || trimmed.contains("how much") || trimmed.contains("what merchant") || trimmed.contains("top merchant")
                || trimmed.contains("spend")) {
            return false;
        }
        return trimmed.length() <= 25;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String jsonParams) {
        if (jsonParams == null || jsonParams.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(jsonParams, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
