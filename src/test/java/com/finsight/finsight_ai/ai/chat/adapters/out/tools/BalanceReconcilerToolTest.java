package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.application.ToolRegistry;
import com.finsight.finsight_ai.ai.chat.domain.BalanceReconciliationResult;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.domain.ToolExecutionResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import com.finsight.finsight_ai.ai.chat.support.UserPromptContext;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.repository.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceReconcilerToolTest {

    private final FinancialAnalyticsPort analyticsPort = mock(FinancialAnalyticsPort.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final UUID userId = UUID.randomUUID();

    private BalanceReconcilerTool tool;

    @BeforeEach
    void setUp() {
        tool = new BalanceReconcilerTool(analyticsPort, accountRepository);
        TenantContext.set(userId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserPromptContext.clear();
    }

    @Test
    void omittedAccountIdUsesOnlyActiveAccountAndPreservesOpeningBalanceProvenance() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account(accountId)));
        when(analyticsPort.reconcileBalance(
                userId,
                accountId,
                new BigDecimal("50000"),
                BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED))
                .thenReturn(new BalanceReconciliationResult(
                        userId,
                        accountId,
                        "INR Checking",
                        "INR",
                        new BigDecimal("50000"),
                        BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED,
                        BigDecimal.ZERO,
                        new BigDecimal("1000"),
                        new BigDecimal("49000"),
                        new BigDecimal("49000"),
                        BigDecimal.ZERO,
                        true,
                        "RECONCILED"
                ));

        UserPromptContext.set("My opening balance was INR 50,000. Why doesn't my account reconcile?");

        var result = tool.execute(Map.of("startingBalance", new BigDecimal("50000")));

        assertThat((BalanceReconciliationResult) result.data())
                .extracting(BalanceReconciliationResult::startingBalanceSource)
                .isEqualTo(BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED);
        assertThat(result.numericEvidence())
                .extracting(evidence -> evidence.field())
                .contains("startingBalance", "expectedEndingBalance", "difference");
        verify(analyticsPort).reconcileBalance(
                userId,
                accountId,
                new BigDecimal("50000"),
                BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED);
    }

    @Test
    void omittedAccountIdFailsClosedWhenNoTenantAccountExists() {
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of());
        ToolRegistry registry = new ToolRegistry(List.of(tool), new ObjectMapper().findAndRegisterModules());

        ToolExecutionResult result = registry.execute(new ToolCallRequest(
                "call-1", "balance_reconciler", Map.of()));

        assertThat(result.status()).isEqualTo(ToolExecutionResult.Status.MODEL_CORRECTABLE_ERROR);
        assertThat(result.errorCode()).isEqualTo("ACCOUNT_ID_REQUIRED");
    }

    @Test
    void omittedAccountIdFailsClosedWhenMultipleAccountsExist() {
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(
                account(UUID.randomUUID()),
                account(UUID.randomUUID())));
        ToolRegistry registry = new ToolRegistry(List.of(tool), new ObjectMapper().findAndRegisterModules());

        ToolExecutionResult result = registry.execute(new ToolCallRequest(
                "call-1", "balance_reconciler", Map.of("startingBalance", new BigDecimal("50000"))));

        assertThat(result.status()).isEqualTo(ToolExecutionResult.Status.MODEL_CORRECTABLE_ERROR);
        assertThat(result.errorCode()).isEqualTo("ACCOUNT_ID_REQUIRED");
    }

    @Test
    void foreignTenantAccountsDoNotAffectSingleTenantAccountInference() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account(accountId)));
        when(analyticsPort.reconcileBalance(
                userId,
                accountId,
                null,
                BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE))
                .thenReturn(new BalanceReconciliationResult(
                        userId,
                        accountId,
                        "INR Checking",
                        "INR",
                        null,
                        BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        null,
                        BigDecimal.ZERO,
                        null,
                        false,
                        "INSUFFICIENT_DATA"
                ));

        var result = tool.execute(Map.of());

        assertThat(result.data()).isInstanceOf(BalanceReconciliationResult.class);
        verify(accountRepository).findAllByUserId(userId);
        verify(analyticsPort).reconcileBalance(
                userId,
                accountId,
                null,
                BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE);
    }

    @Test
    void expectedEndingBalanceEvidenceRequiresTrustedStartingBalanceProvenance() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account(accountId)));
        when(analyticsPort.reconcileBalance(
                userId,
                accountId,
                null,
                BalanceReconciliationResult.StartingBalanceSource.UNVERIFIED))
                .thenReturn(new BalanceReconciliationResult(
                        userId,
                        accountId,
                        "INR Checking",
                        "INR",
                        null,
                        BalanceReconciliationResult.StartingBalanceSource.UNVERIFIED,
                        BigDecimal.ZERO,
                        new BigDecimal("1000"),
                        new BigDecimal("49000"),
                        new BigDecimal("50000"),
                        new BigDecimal("1000"),
                        false,
                        "INSUFFICIENT_DATA"
                ));

        UserPromptContext.set("I spent INR 50,000 last month. Why doesn't my account reconcile?");

        var result = tool.execute(Map.of("startingBalance", new BigDecimal("50000")));

        assertThat(result.numericEvidence())
                .extracting(evidence -> evidence.field())
                .doesNotContain("startingBalance", "expectedEndingBalance", "difference")
                .contains("totalExpense", "actualEndingBalance");
    }

    @Test
    void expectedEndingBalanceEvidenceIsNotAddedWhenStartingBalanceUnavailable() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account(accountId)));
        when(analyticsPort.reconcileBalance(
                userId,
                accountId,
                null,
                BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE))
                .thenReturn(new BalanceReconciliationResult(
                        userId,
                        accountId,
                        "INR Checking",
                        "INR",
                        null,
                        BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE,
                        BigDecimal.ZERO,
                        new BigDecimal("1000"),
                        new BigDecimal("49000"),
                        new BigDecimal("50000"),
                        new BigDecimal("1000"),
                        false,
                        "INSUFFICIENT_DATA"
                ));

        var result = tool.execute(Map.of());

        assertThat(result.numericEvidence())
                .extracting(evidence -> evidence.field())
                .doesNotContain("startingBalance", "expectedEndingBalance", "difference")
                .contains("totalExpense", "actualEndingBalance");
    }

    private static Account account(UUID id) {
        Account account = new Account();
        account.setId(id);
        return account;
    }
}
