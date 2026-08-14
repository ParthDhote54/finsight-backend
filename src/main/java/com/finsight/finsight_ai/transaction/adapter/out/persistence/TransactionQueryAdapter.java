package com.finsight.finsight_ai.transaction.adapter.out.persistence;

import com.finsight.finsight_ai.transaction.application.port.in.TransactionQueryPort;
import com.finsight.finsight_ai.transaction.domain.view.TransactionView;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TransactionQueryAdapter implements TransactionQueryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // RowMapper mapping SQL result sets directly into immutable TransactionView records
    private final RowMapper<TransactionView> rowMapper = (rs, rowNum) -> new TransactionView(
            rs.getObject("id", UUID.class),
            rs.getObject("user_id", UUID.class), // Securely retrieved from the joined accounts table
            rs.getBigDecimal("amount"),
            rs.getString("description"),
            rs.getObject("category_id", UUID.class),
            rs.getDate("transaction_date") != null ? rs.getDate("transaction_date").toLocalDate() : null
    );

    @Override
    public Optional<TransactionView> getTransaction(UUID transactionId, UUID userId) {
        // FIX 9: Multi-tenant ownership verification via accounts JOIN
        String sql = """
                SELECT t.id, a.user_id, t.amount, t.description, t.category_id, t.transaction_date 
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE t.id = :transactionId AND a.user_id = :userId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("transactionId", transactionId)
                .addValue("userId", userId);

        List<TransactionView> results = jdbcTemplate.query(sql, params, rowMapper);

        return results.stream().findFirst();
    }

    @Override
    public List<TransactionView> getTransactions(List<UUID> transactionIds, UUID userId) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return Collections.emptyList();
        }

        // FIX 9: Multi-tenant ownership verification via accounts JOIN
        String sql = """
                SELECT t.id, a.user_id, t.amount, t.description, t.category_id, t.transaction_date
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE t.id IN (:transactionIds) AND a.user_id = :userId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("transactionIds", transactionIds);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public List<TransactionView> getRecentTransactions(UUID userId, int limit) {
        // FIX 11: Hard-cap safety guard to max 50 rows
        int safeLimit = (limit <= 0 || limit > 50) ? 50 : limit;

        // FIX 9: Multi-tenant ownership verification via accounts JOIN
        String sql = """
                SELECT t.id, a.user_id, t.amount, t.description, t.category_id, t.transaction_date
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE a.user_id = :userId
                ORDER BY t.transaction_date DESC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", safeLimit);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    @Override
    public List<TransactionView> search(UUID userId, String searchTerm, int limit) {
        // FIX 11: Hard-cap safety guard (max 50 rows, no expensive COUNT queries)
        int safeLimit = (limit <= 0 || limit > 50) ? 50 : limit;

        // FIX 9: Multi-tenant ownership verification via accounts JOIN
        String sql = """
                SELECT t.id, a.user_id, t.amount, t.description, t.category_id, t.transaction_date
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE a.user_id = :userId
                  AND (:searchTerm IS NULL OR LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
                ORDER BY t.transaction_date DESC
                LIMIT :limit
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("searchTerm", searchTerm)
                .addValue("limit", safeLimit);

        return jdbcTemplate.query(sql, params, rowMapper);
    }
}