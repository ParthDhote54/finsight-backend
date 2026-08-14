package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PgVectorSearchAdapter implements VectorSearchPort {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<ScoredTransaction> similaritySearch(UUID userId, float[] queryEmbedding, int topK, double minScore) {
        String embeddingString = arrayToVectorString(queryEmbedding);

        String sql = """
                SELECT t.id,
                       a.user_id,
                       t.description as merchant_name,
                       c.name as category,
                       t.amount,
                       t.description,
                       1 - (v.embedding <=> cast(:embedding as vector)) as similarity
                FROM transaction_vectors v
                JOIN transactions t ON v.transaction_id = t.id
                JOIN accounts a ON t.account_id = a.id
                LEFT JOIN categories c ON t.category_id = c.id
                WHERE a.user_id = :userId
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND (c.id IS NULL OR c.deleted_at IS NULL)
                  AND (1 - (v.embedding <=> cast(:embedding as vector))) >= :minScore
                ORDER BY similarity DESC
                LIMIT :topK
                """;

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("embedding", embeddingString)
                .setParameter("minScore", minScore)
                .setParameter("topK", topK)
                .getResultList();

        return rows.stream()
                .map(row -> new ScoredTransaction(
                        (UUID) row[0],
                        (UUID) row[1],
                        (String) row[2],
                        (String) row[3],
                        ((Number) row[4]).doubleValue(),
                        (String) row[5],
                        ((Number) row[6]).doubleValue()
                ))
                .toList();
    }

    private String arrayToVectorString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            sb.append(array[i]);
            if (i < array.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
