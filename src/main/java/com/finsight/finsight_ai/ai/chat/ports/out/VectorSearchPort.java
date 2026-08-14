package com.finsight.finsight_ai.ai.chat.ports.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for vector similarity search against the transaction_vectors table.
 *
 * <p>This is a repository-layer contract — it receives an explicit userId parameter
 * (consistent with existing repository security patterns where queries join on
 * account.user.id) rather than reading from TenantContext. TenantContext is used
 * at the tool layer; repository calls use explicit parameters for testability
 * and clarity.
 */
public interface VectorSearchPort {

    /**
     * Finds transactions whose embeddings are most similar to the query embedding.
     *
     * @param userId       the authenticated tenant — explicit parameter, never from LLM
     * @param queryEmbedding the embedding vector for the user's query
     * @param topK         maximum number of results to return
     * @param minScore     minimum cosine similarity threshold (0.0 to 1.0)
     * @return scored transactions ordered by descending similarity
     */
    List<ScoredTransaction> similaritySearch(UUID userId, float[] queryEmbedding, int topK, double minScore);

    /**
     * A transaction result scored by vector similarity.
     */
    record ScoredTransaction(
            UUID transactionId,
            UUID userId,
            String merchant,
            String category,
            Double amount,
            String description,
            double similarityScore
    ) {}
}
