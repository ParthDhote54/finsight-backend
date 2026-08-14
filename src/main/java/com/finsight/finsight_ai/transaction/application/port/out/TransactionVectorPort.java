package com.finsight.finsight_ai.transaction.application.port.out;

import java.util.List;
import java.util.UUID;

public interface TransactionVectorPort {
    /*
    * checks if a vector already exists for this transaction with the exact same content path.
    * prevents re-calling google vertex AI and save api costs.
     */
    boolean hasVectorForHash(UUID transactionId, String contentHash);


    /*
     *Inserts of updates the 768-dimensional vector representation in PostgresSQL pgVector.
     */
    void upsertVector(UUID transactionId, UUID userId, String contentHash, float[] embedding);


    /*
    *Executes cosine similarity search over transaction_vectors(Used during RAG chat in Phase 4).
     */

    List<UUID> findSimilarTransactionIds(UUID userId, float[] queryEmbedding, double SimilarityThreshold, int limit);

    void deleteVector(UUID transactionId);
}
