package com.finsight.finsight_ai.Mapper;

import com.finsight.finsight_ai.dto.TransactionRequest;
import com.finsight.finsight_ai.dto.TransactionResponse;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.entity.Transaction;

public class TransactionMapper {

    public static Transaction requestDtoToTransaction(TransactionRequest request, Account account, Category category){

        return Transaction.builder()
                .amount(request.getAmount())
                .description(request.getDescription())
                .category(category)
                .transactionType(request.getTransactionType())
                .account(account)
                .build();

    }

    public static TransactionResponse TransactionToResponseDto(Transaction transaction){

        // Inside TransactionMapper.TransactionToResponseDto(...)

        return TransactionResponse.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccount().getId())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())

                // NEW: Safe Null Check! If category is not null, get the name. Otherwise, return null (or "Uncategorized")
                .category(transaction.getCategory() != null ? transaction.getCategory().getName() : "Uncategorized")

                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate().atStartOfDay())
                .newAccountBalance(transaction.getAccount().getBalance())// Or however you mapped this
                // ... any other fields
                .build();
    }
}
