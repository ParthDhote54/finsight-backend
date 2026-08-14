package com.finsight.finsight_ai.Controller;

import com.finsight.finsight_ai.Service.TransactionService;
import com.finsight.finsight_ai.dto.TransactionRequest;
import com.finsight.finsight_ai.dto.TransactionResponse;
import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.transaction.InvalidTransactionException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest transactionRequest,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("event=CREATE_TRANSACTION_REQUEST | user='{}'", principal.getEmail());

        TransactionResponse transactionResponse = transactionService.createTransaction(transactionRequest, principal.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(transactionResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable("id") UUID transactionId,
            @Valid @RequestBody TransactionRequest transactionRequest,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("event=UPDATE_TRANSACTION_REQUEST | id='{}' | user='{}'", transactionId, principal.getEmail());

        TransactionResponse transactionResponse = transactionService.updateTransaction(transactionId, transactionRequest, principal.getUserId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(transactionResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable("id") UUID transactionId,
            @AuthenticationPrincipal UserPrincipal principal) throws InvalidTransactionException {

        log.info("event=DELETE_TRANSACTION_REQUEST | id='{}' | user='{}'", transactionId, principal.getEmail());

        transactionService.deleteTransaction(transactionId, principal.getUserId());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable("id") UUID transactionId,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.info("event=GET_TRANSACTION_REQUEST | id='{}' | user='{}'", transactionId, principal.getEmail());

        TransactionResponse transactionResponse = transactionService.getTransaction(transactionId, principal.getUserId());

        return ResponseEntity.ok(transactionResponse);
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getAllTransactions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = {"transactionDate", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("event=GET_ALL_TRANSACTIONS_REQUEST | user='{}' | page='{}'", principal.getEmail(), pageable.getPageNumber());

        Page<TransactionResponse> transactions = transactionService.getAllTransactions(principal.getUserId(), pageable);

        return ResponseEntity.ok(transactions);
    }
}
