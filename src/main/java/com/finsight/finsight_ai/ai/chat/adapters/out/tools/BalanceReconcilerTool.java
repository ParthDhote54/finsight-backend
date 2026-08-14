package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.BalanceReconciliationResult;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import com.finsight.finsight_ai.ai.chat.support.UserPromptContext;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BalanceReconcilerTool implements FinanceTool {

    private final FinancialAnalyticsPort analyticsPort;
    private final AccountRepository accountRepository;

    public BalanceReconcilerTool(FinancialAnalyticsPort analyticsPort,
                                 AccountRepository accountRepository) {
        this.analyticsPort = analyticsPort;
        this.accountRepository = accountRepository;
    }

    @Override
    public String name() {
        return "balance_reconciler";
    }

    @Override
    public String description() {
        return "Use for account balance reconciliation, balance discrepancy, or account-does-not-reconcile questions. "
                + "Reconciles stored account balance against deterministic income and expense activity. "
                + "Only pass startingBalance when the user explicitly says opening, starting, initial, or beginning balance; "
                + "do not treat spending, payment, expense, or transfer amounts as startingBalance.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "accountId", Map.of("type", "string", "pattern", "^[0-9a-fA-F-]{36}$",
                                "description", "Account UUID to reconcile. Optional only when the user has exactly one active account."),
                        "startingBalance", Map.of("type", "number",
                                "description", "Historical opening/starting/initial balance for the reconciliation period. "
                                        + "Only set this when the user explicitly provides it as an opening/starting/initial/beginning balance.")
                ),
                "required", List.of(),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        UUID accountId = resolveAccountId(userId, args);
        BigDecimal rawStartingBalance = ToolArguments.optionalPositiveDecimal(args, "startingBalance");

        BalanceReconciliationResult.StartingBalanceSource source =
                BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE;
        BigDecimal startingBalance = null;

        if (rawStartingBalance != null) {
            String userPrompt = UserPromptContext.get();
            if (isExplicitInUserPrompt(rawStartingBalance, userPrompt)) {
                source = BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED;
                startingBalance = rawStartingBalance;
            } else {
                source = BalanceReconciliationResult.StartingBalanceSource.UNVERIFIED;
            }
        }

        BalanceReconciliationResult result = analyticsPort.reconcileBalance(userId, accountId, startingBalance, source);

        List<NumericEvidence> evidence = new java.util.ArrayList<>();
        if (result.startingBalance() != null && trustedStartingBalance(result.startingBalanceSource())) {
            evidence.add(NumericEvidence.monetary(name(), "startingBalance", result.startingBalance(), result.currency()));
        }
        evidence.add(NumericEvidence.monetary(name(), "totalIncome", result.totalIncome(), result.currency()));
        evidence.add(NumericEvidence.monetary(name(), "totalExpense", result.totalExpense(), result.currency()));
        if (result.expectedEndingBalance() != null && trustedStartingBalance(result.startingBalanceSource())) {
            evidence.add(NumericEvidence.monetary(name(), "expectedEndingBalance", result.expectedEndingBalance(), result.currency()));
        }
        evidence.add(NumericEvidence.monetary(name(), "actualEndingBalance", result.actualEndingBalance(), result.currency()));
        if (result.difference() != null && trustedStartingBalance(result.startingBalanceSource())) {
            evidence.add(NumericEvidence.monetary(name(), "difference", result.difference(), result.currency()));
        }

        return FinanceToolResult.of(result, evidence);
    }

    private static boolean trustedStartingBalance(BalanceReconciliationResult.StartingBalanceSource source) {
        return source == BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED
                || source == BalanceReconciliationResult.StartingBalanceSource.PERSISTED_TRUSTED;
    }

    private UUID resolveAccountId(UUID userId, Map<String, Object> args) {
        if (args.containsKey("accountId") && args.get("accountId") != null) {
            return ToolArguments.requiredUuid(args, "accountId");
        }
        List<Account> accounts = accountRepository.findAllByUserId(userId);
        if (accounts.size() == 1) {
            return accounts.get(0).getId();
        }
        throw new com.finsight.finsight_ai.ai.chat.domain.tools.ToolArgumentException(
                "ACCOUNT_ID_REQUIRED",
                "accountId",
                "accountId is required when the user has zero or multiple active accounts");
    }

    public static boolean isExplicitInUserPrompt(BigDecimal amount, String userPrompt) {
        if (amount == null || userPrompt == null || userPrompt.isBlank()) {
            return false;
        }
        String normalizedPrompt = userPrompt.replaceAll("[₹$,]", "").toLowerCase(java.util.Locale.ROOT);

        java.util.regex.Pattern openingIntent = java.util.regex.Pattern.compile(
                "\\b(open|opening|start|starting|started|initial|began|beginning)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        if (!openingIntent.matcher(normalizedPrompt).find()) {
            return false;
        }

        String valStr = amount.stripTrailingZeros().toPlainString().toLowerCase(java.util.Locale.ROOT);
        String intStr = amount.toBigInteger().toString();

        if (normalizedPrompt.contains(valStr) || normalizedPrompt.contains(intStr)) {
            return true;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(normalizedPrompt);
        while (matcher.find()) {
            try {
                BigDecimal candidate = new BigDecimal(matcher.group());
                if (candidate.compareTo(amount) == 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
