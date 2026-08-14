package com.finsight.finsight_ai.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.dto.TransactionRequest;
import com.finsight.finsight_ai.dto.TransactionResponse;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.entity.Transaction;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.entity.User;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import com.finsight.finsight_ai.repository.AccountRepository;
import com.finsight.finsight_ai.repository.CategoryRepository;
import com.finsight.finsight_ai.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                transactionRepository,
                categoryRepository,
                accountRepository,
                jdbcTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void rejectsNullZeroNegativeAndOverPrecisionAmountsBeforePersistence() {
        TransactionRequest request = request(TransactionType.EXPENSE, new BigDecimal("1.00"));

        request.setAmount(null);
        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));
        request.setAmount(BigDecimal.ZERO);
        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));
        request.setAmount(new BigDecimal("-1.00"));
        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));
        request.setAmount(new BigDecimal("1.00001"));
        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));

        verifyNoInteractions(accountRepository, categoryRepository, transactionRepository, jdbcTemplate);
    }

    @Test
    void rejectsMissingTypeAndUnsupportedTransferBeforePersistence() {
        TransactionRequest request = request(null, new BigDecimal("10.00"));
        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));

        request.setTransactionType(TransactionType.TRANSFER);
        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));

        verifyNoInteractions(accountRepository, categoryRepository, transactionRepository, jdbcTemplate);
    }

    @Test
    void createExpenseUsesTenantScopedAccountAndExactBigDecimalArithmetic() {
        Account account = account(new BigDecimal("100.00"));
        TransactionRequest request = request(TransactionType.EXPENSE, new BigDecimal("10.10"));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(TRANSACTION_ID);
            transaction.setTransactionDate(LocalDate.of(2026, 7, 1));
            return transaction;
        });

        TransactionResponse response = service.createTransaction(request, USER_ID);

        assertEquals(0, new BigDecimal("89.90").compareTo(account.getBalance()));
        assertEquals(0, new BigDecimal("10.10").compareTo(response.getAmount()));
        verify(accountRepository).findByIdAndUserId(ACCOUNT_ID, USER_ID);
        verify(accountRepository).save(account);
        verify(transactionRepository).save(any(Transaction.class));
        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any());
    }

    @Test
    void inaccessibleAccountIsIndistinguishableFromMissingAccount() {
        TransactionRequest request = request(TransactionType.INCOME, new BigDecimal("10.00"));
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createTransaction(request, USER_ID));

        verify(accountRepository).findByIdAndUserId(ACCOUNT_ID, USER_ID);
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(categoryRepository, transactionRepository, jdbcTemplate);
    }

    @Test
    void anotherUsersPrivateCategoryCannotBeAssigned() {
        UUID categoryId = UUID.randomUUID();
        Account account = account(new BigDecimal("100.00"));
        TransactionRequest request = request(TransactionType.EXPENSE, new BigDecimal("10.00"));
        request.setCategoryId(categoryId);
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(categoryRepository.findAccessibleById(categoryId, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createTransaction(request, USER_ID));

        verify(categoryRepository).findAccessibleById(categoryId, USER_ID);
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, jdbcTemplate);
    }

    @Test
    void categoryTypeMustMatchTransactionType() {
        UUID categoryId = UUID.randomUUID();
        Account account = account(new BigDecimal("100.00"));
        Category category = new Category();
        category.setId(categoryId);
        category.setType(TransactionType.INCOME);
        TransactionRequest request = request(TransactionType.EXPENSE, new BigDecimal("10.00"));
        request.setCategoryId(categoryId);
        when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).thenReturn(Optional.of(account));
        when(categoryRepository.findAccessibleById(categoryId, USER_ID)).thenReturn(Optional.of(category));

        assertThrows(IllegalStateException.class, () -> service.createTransaction(request, USER_ID));

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, jdbcTemplate);
    }

    @Test
    void pageQueryUsesAuthenticatedUserAndPreservesPageMetadata() {
        Account account = account(new BigDecimal("100.00"));
        Transaction transaction = Transaction.builder()
                .id(TRANSACTION_ID)
                .account(account)
                .amount(new BigDecimal("5.00"))
                .transactionType(TransactionType.EXPENSE)
                .transactionDate(LocalDate.of(2026, 7, 1))
                .build();
        Pageable pageable = PageRequest.of(2, 5, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id")));
        when(transactionRepository.findAllByAccountUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction, transaction), pageable, 12));

        Page<TransactionResponse> result = service.getAllTransactions(USER_ID, pageable);

        assertEquals(2, result.getNumber());
        assertEquals(5, result.getSize());
        assertEquals(12, result.getTotalElements());
        assertEquals(TRANSACTION_ID, result.getContent().get(0).getId());
        verify(transactionRepository).findAllByAccountUserId(USER_ID, pageable);
    }

    private TransactionRequest request(TransactionType type, BigDecimal amount) {
        TransactionRequest request = new TransactionRequest();
        request.setAccountId(ACCOUNT_ID);
        request.setTransactionType(type);
        request.setAmount(amount);
        return request;
    }

    private Account account(BigDecimal balance) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("owner@example.com");
        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUser(user);
        account.setBalance(balance);
        return account;
    }
}
