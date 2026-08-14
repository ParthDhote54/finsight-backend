package com.finsight.finsight_ai.transaction.application.port.in;


import com.finsight.finsight_ai.transaction.domain.view.TransactionView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 *The read-only port for th transaction module.
 * the AI and analytics modules must use this interface.
 * They are strictly forbidden from importing the JPA repository.
 */
public interface TransactionQueryPort {
   /*
   Fetches a single transaction for background processing(e.g., categorization outbox.)
   Enforces a tenant isolation by requiring the userId.
    */

    Optional<TransactionView> getTransaction(UUID transactionId, UUID userId);

    /*
    Fetches a batch of transaction by their IDs.
    Used by the AI tool Calling (RAG) to fetch specific records after a vector search.
     */

    List<TransactionView>getTransactions(List<UUID> transactionIds, UUID userId);

    /*
    Fetches recent transaction for a user, strictly bounded by a limit.
    Prevents the AI from accidentally loading 10,000 records and blowing the token budget.
     */
    List<TransactionView>getRecentTransactions(UUID userId, int limit);

    List<TransactionView> search(UUID userId, String searchTerm, int limit);

}
