package com.finsight.finsight_ai.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.Mapper.TransactionMapper;
import com.finsight.finsight_ai.dto.TransactionRequest;
import com.finsight.finsight_ai.dto.TransactionResponse;
import com.finsight.finsight_ai.entity.*;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import com.finsight.finsight_ai.repository.AccountRepository;
import com.finsight.finsight_ai.repository.CategoryRepository;
import com.finsight.finsight_ai.repository.TransactionRepository;
import jakarta.transaction.InvalidTransactionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest transactionRequest, UUID userId) {

        validateTransactionRequest(transactionRequest);
        Account account = accountRepository.findByIdAndUserId(transactionRequest.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        Category category = resolveAccessibleCategory(
                transactionRequest.getCategoryId(), userId, transactionRequest.getTransactionType());

        // 1. Balance Calculation
        if (transactionRequest.getTransactionType() == TransactionType.INCOME) {
            account.setBalance(account.getBalance().add(transactionRequest.getAmount()));
        } else if (transactionRequest.getTransactionType() == TransactionType.EXPENSE) {
            if (account.getBalance().compareTo(transactionRequest.getAmount()) < 0) {
                throw new IllegalStateException("INSUFFICIENT BALANCE");
            }
            account.setBalance(account.getBalance().subtract(transactionRequest.getAmount()));
        }

        accountRepository.save(account);

        // 2. Save Core Transaction Entity
        Transaction transaction = TransactionMapper.requestDtoToTransaction(transactionRequest, account, category);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // 3. Emit Outbox Event: TRANSACTION_CREATED
        publishOutboxEvent(
                savedTransaction.getId(),
                "TRANSACTION_CREATED",
                Map.of(
                        "transactionId", savedTransaction.getId(),
                        "userId", account.getUser().getId(),
                        "accountId", account.getId(),
                        "amount", savedTransaction.getAmount(),
                        "description", savedTransaction.getDescription() == null ? "" : savedTransaction.getDescription(),
                        "transactionType", savedTransaction.getTransactionType().name(),
                        "transactionDate", savedTransaction.getTransactionDate().toString(),
                        "categoryId", category != null ? category.getId() : ""
                )
        );

        return TransactionMapper.TransactionToResponseDto(savedTransaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(UUID transactionId, TransactionRequest transactionRequest, UUID userId) {

        validateTransactionRequest(transactionRequest);
        Transaction transaction = transactionRepository.findByIdAndUserIdWithAccountJoin(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (!transaction.getAccount().getId().equals(transactionRequest.getAccountId())) {
            throw new IllegalStateException("A transaction cannot be moved to another account");
        }

        if (transaction.getTransactionType() != transactionRequest.getTransactionType()) {
            throw new IllegalStateException("Cannot change fundamental type of a transaction.");
        }

        Account account = transaction.getAccount();
        BigDecimal diff = transactionRequest.getAmount().subtract(transaction.getAmount());

        if (transaction.getTransactionType() == TransactionType.INCOME) {
            BigDecimal newBalance = account.getBalance().add(diff);
            if (newBalance.signum() >= 0) {
                account.setBalance(newBalance);
            } else {
                throw new IllegalStateException("This adjustment results in a negative balance");
            }
        } else if (transaction.getTransactionType() == TransactionType.EXPENSE) {
            BigDecimal newBalance = account.getBalance().subtract(diff);
            if (newBalance.signum() >= 0) {
                account.setBalance(newBalance);
            } else {
                throw new IllegalStateException("This adjustment results in a negative balance");
            }
        } else if (!transaction.getAmount().equals(transactionRequest.getAmount())) {
            throw new IllegalStateException("Cannot update amount of transferred fund.");
        }

        accountRepository.save(account);

        Category newCategory = resolveAccessibleCategory(
                transactionRequest.getCategoryId(), userId, transactionRequest.getTransactionType());

        // Mutate existing Managed Entity
        transaction.setAmount(transactionRequest.getAmount());
        transaction.setCategory(newCategory);
        transaction.setDescription(transactionRequest.getDescription());

        Transaction updatedTransaction = transactionRepository.save(transaction);

        // Emit Outbox Event: TRANSACTION_UPDATED
        publishOutboxEvent(
                updatedTransaction.getId(),
                "TRANSACTION_UPDATED",
                Map.of(
                        "transactionId", updatedTransaction.getId(),
                        "userId", account.getUser().getId(),
                        "amount", updatedTransaction.getAmount(),
                        "description", updatedTransaction.getDescription() == null ? "" : updatedTransaction.getDescription(),
                        "categoryId", newCategory != null ? newCategory.getId() : ""
                )
        );

        return TransactionMapper.TransactionToResponseDto(updatedTransaction);
    }

    @Transactional
    public void deleteTransaction(UUID transactionId, UUID userId) throws InvalidTransactionException {

        Transaction transaction = transactionRepository.findByIdAndUserIdWithAccountJoin(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        Account account = transaction.getAccount();
        if (transaction.getTransactionType().equals(TransactionType.INCOME)) {
            if (account.getBalance().compareTo(transaction.getAmount()) >= 0) {
                account.setBalance(account.getBalance().subtract(transaction.getAmount()));
            } else {
                throw new InvalidTransactionException("Insufficient Balance");
            }
        } else if (transaction.getTransactionType().equals(TransactionType.EXPENSE)) {
            account.setBalance(account.getBalance().add(transaction.getAmount()));
        } else {
            throw new InvalidTransactionException("Transaction cannot revert back");
        }

        accountRepository.save(account);

        transactionRepository.delete(transaction);

        // Emit Outbox Event: TRANSACTION_DELETED
        publishOutboxEvent(
                transactionId,
                "TRANSACTION_DELETED",
                Map.of(
                        "transactionId", transactionId,
                        "userId", userId
                )
        );
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID transactionId, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserIdWithAccountJoin(transactionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        return TransactionMapper.TransactionToResponseDto(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions(
            UUID accountId,
            LocalDate startDate,
            LocalDate endDate,
            UUID userId
    ) {
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (endDate == null) endDate = LocalDate.now();
        if (startDate == null) startDate = endDate.minusMonths(1);
        if (startDate.isAfter(endDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        List<Transaction> transactionList = transactionRepository.findAllByAccountIdAndTransactionDateBetweenOrderByTransactionDateDesc(accountId, startDate, endDate);
        return transactionList.stream().map(TransactionMapper::TransactionToResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllTransactions(UUID userId, Pageable pageable) {
        Page<Transaction> transactionPage = transactionRepository.findAllByAccountUserId(userId, pageable);
        return transactionPage.map(TransactionMapper::TransactionToResponseDto);
    }

    private void validateTransactionRequest(TransactionRequest request) {
        if (request == null || request.getAccountId() == null) {
            throw new IllegalStateException("Account is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalStateException("Amount must be strictly greater than 0");
        }
        BigDecimal normalizedAmount = request.getAmount().stripTrailingZeros();
        int fractionDigits = Math.max(normalizedAmount.scale(), 0);
        int integerDigits = Math.max(normalizedAmount.precision() - normalizedAmount.scale(), 0);
        if (fractionDigits > 4 || integerDigits > 15) {
            throw new IllegalStateException("Amount supports at most 15 integer and 4 decimal digits");
        }
        if (request.getTransactionType() == null) {
            throw new IllegalStateException("Transaction type is required");
        }
        if (request.getTransactionType() == TransactionType.TRANSFER) {
            throw new IllegalStateException("TRANSFER transactions require a dedicated transfer workflow");
        }
    }

    private Category resolveAccessibleCategory(UUID categoryId, UUID userId, TransactionType transactionType) {
        if (categoryId == null) {
            return null;
        }

        Category category = categoryRepository.findAccessibleById(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getType() != transactionType) {
            throw new IllegalStateException("Category type must match transaction type");
        }
        return category;
    }

    // --- Helper Method to Persist Outbox Events Atomically ---
    private void publishOutboxEvent(UUID aggregateId, String eventType, Map<String, Object> payloadMap) {
        try {
            UUID eventId = UUID.randomUUID();
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);

            String sql = """
                INSERT INTO outbox_events (event_id, aggregate_id, event_type, payload, status)
                VALUES (?, ?, ?, ?::jsonb, 'PENDING')
                """;

            jdbcTemplate.update(sql, eventId, aggregateId, eventType, jsonPayload);

            log.info("event=OUTBOX_EVENT_PERSISTED | eventType='{}' | eventId='{}' | aggregateId='{}'",
                    eventType, eventId, aggregateId);

        } catch (JsonProcessingException e) {
            log.error("event=OUTBOX_SERIALIZATION_FAILED | aggregateId='{}'", aggregateId, e);
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
