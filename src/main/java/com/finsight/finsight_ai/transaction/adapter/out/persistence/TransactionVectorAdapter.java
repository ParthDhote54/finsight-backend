package com.finsight.finsight_ai.transaction.adapter.out.persistence;
import com.finsight.finsight_ai.transaction.application.port.out.TransactionVectorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class TransactionVectorAdapter implements TransactionVectorPort {
    private final NamedParameterJdbcTemplate jdbcTemplate;


    @Override
    @Transactional(readOnly = true)
    public boolean hasVectorForHash(UUID transactionId, String contentHash) {
        String sql = """
                SELECT COUNT(1) FROM transaction_vectors
                WHERE transaction_id = :transactionId AND content_hash = :contentHash
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("contentHash", contentHash);

        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void upsertVector(UUID transactionId, UUID userId, String contentHash, float[] embedding) {
        String vectorString = Arrays.toString(embedding);

        String sql = """
                INSERT INTO transaction_vectors
                (transaction_id, user_id, content_hash,embedding,embedding_model,embedding_version)
                VALUES
                (:transactionId, :userId, :contentHash, :embedding::vector, 'text-embedding-004', 1)
                ON CONFLICT (transaction_id) DO UPDATE
                SET embedding = EXCLUDED.embedding,
                    content_hash = EXCLUDED.content_hash;
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("userId", userId)
                .addValue("contentHash", contentHash)
                .addValue("embedding", vectorString);

        jdbcTemplate.update(sql, params);
        log.info("event=VECTOR_UPSERTED_SUCCESSFULLY | transactionId = {}", transactionId);


    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findSimilarTransactionIds(UUID userId, float[] queryEmbedding, double similarityThreshold, int limit) {
        String vectorString = Arrays.toString(queryEmbedding);

        //using PostgresSQL pgVector cosine Distance operator(<=>)
        //cosine similarity = 1 - cosine distance.

        String sql = """
                SELECT transaction_id
                FROM transaction_vectors
                WHERE user_id = :userId
                AND (1 - (embedding <=> :queryVector::vector)) >= :threshold
                ORDER BY embedding <=> :queryVector::vector ASC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("queryVector", vectorString)
                .addValue("threshold", similarityThreshold)
                .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getObject("transaction_id", UUID.class));
    }

    @Override
    @Transactional
    public void deleteVector(UUID transactionId) {
        String sql = "DELETE FROM transaction_vectors WHERE transaction_id = :transactionId";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("transactionId", transactionId);
        jdbcTemplate.update(sql, params);
        log.info("event=VECTOR_DELETED_SUCCESSFULLY | transactionId = {}", transactionId);
    }
}
