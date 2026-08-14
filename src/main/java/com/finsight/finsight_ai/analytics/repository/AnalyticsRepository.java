package com.finsight.finsight_ai.analytics.repository;

import com.finsight.finsight_ai.analytics.projection.CategoryTotalProjection;
import com.finsight.finsight_ai.analytics.projection.TrendProjection;
import com.finsight.finsight_ai.analytics.projection.TypeTotalProjection;
import com.finsight.finsight_ai.entity.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<TypeTotalProjection> sumByTypeInRange(UUID userId, LocalDate start, LocalDate end) {
        String sql = """
                WITH eligible AS (
                    SELECT t.transaction_type AS type,
                           t.amount,
                           UPPER(a.currency) AS currency
                    FROM transactions t
                    JOIN accounts a ON t.account_id = a.id AND a.deleted_at IS NULL
                    WHERE a.user_id = :userId
                      AND t.transaction_date >= :start
                      AND t.transaction_date <= :end
                      AND t.deleted_at IS NULL
                      AND t.transaction_type IN ('INCOME', 'EXPENSE')
                ),
                currency_stats AS (
                    SELECT MIN(currency) AS minimum_currency,
                           MAX(currency) AS maximum_currency
                    FROM eligible
                )
                SELECT e.type,
                       COALESCE(SUM(e.amount), 0) AS total,
                       cs.minimum_currency,
                       cs.maximum_currency
                FROM eligible e
                CROSS JOIN currency_stats cs
                GROUP BY e.type, cs.minimum_currency, cs.maximum_currency
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("start", start)
                .addValue("end", end);

        return jdbcTemplate.query(sql, params, new DataClassRowMapper<>(TypeTotalProjection.class));
    }

    public List<CategoryTotalProjection> sumByCategoryInRange(UUID userId, TransactionType type, LocalDate start, LocalDate end) {
        String sql = """
                WITH eligible AS (
                    SELECT t.id,
                           t.amount,
                           t.category_id,
                           UPPER(a.currency) AS currency
                    FROM transactions t
                    JOIN accounts a ON t.account_id = a.id AND a.deleted_at IS NULL
                    WHERE a.user_id = :userId
                      AND t.transaction_type = :type
                      AND t.transaction_date >= :start
                      AND t.transaction_date <= :end
                      AND t.deleted_at IS NULL
                ),
                currency_stats AS (
                    SELECT MIN(currency) AS minimum_currency,
                           MAX(currency) AS maximum_currency
                    FROM eligible
                )
                SELECT c.id AS category_id,
                       COALESCE(c.name, 'Uncategorized') AS category_name,
                       COALESCE(SUM(e.amount), 0) AS total,
                       COUNT(e.id) AS count,
                       cs.minimum_currency,
                       cs.maximum_currency
                FROM eligible e
                LEFT JOIN categories c ON e.category_id = c.id
                                      AND c.deleted_at IS NULL
                                      AND (c.user_id IS NULL OR c.user_id = :userId)
                CROSS JOIN currency_stats cs
                GROUP BY c.id, COALESCE(c.name, 'Uncategorized'),
                         cs.minimum_currency, cs.maximum_currency
                ORDER BY total DESC, category_name ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("type", type.name())
                .addValue("start", start)
                .addValue("end", end);

        return jdbcTemplate.query(sql, params, new DataClassRowMapper<>(CategoryTotalProjection.class));
    }

    public List<TrendProjection> monthlyTrend(UUID userId, LocalDate start, LocalDate end) {
        String sql = """
                WITH months AS (
                    SELECT generate_series(
                        date_trunc('month', CAST(:start AS date)),
                        date_trunc('month', CAST(:end AS date)),
                        interval '1 month'
                    ) AS month_start
                ),
                eligible_transactions AS (
                    SELECT t.transaction_date, t.transaction_type, t.amount,
                           UPPER(a.currency) AS currency
                    FROM transactions t
                    JOIN accounts a ON a.id = t.account_id AND a.deleted_at IS NULL
                    WHERE a.user_id = :userId
                      AND t.deleted_at IS NULL
                      AND t.transaction_date >= :start
                      AND t.transaction_date <= :end
                      AND t.transaction_type IN ('INCOME', 'EXPENSE')
                ),
                currency_stats AS (
                    SELECT COALESCE(MIN(currency),
                                    (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                               AS minimum_currency,
                           COALESCE(MAX(currency),
                                    (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                               AS maximum_currency
                    FROM eligible_transactions
                )
                SELECT CAST(m.month_start AS date) AS period_start,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.transaction_type = 'INCOME'), 0) AS total_income,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.transaction_type = 'EXPENSE'), 0) AS total_expense,
                       cs.minimum_currency,
                       cs.maximum_currency
                FROM months m
                CROSS JOIN currency_stats cs
                LEFT JOIN eligible_transactions e
                  ON e.transaction_date >= CAST(m.month_start AS date)
                 AND e.transaction_date < CAST(m.month_start + interval '1 month' AS date)
                GROUP BY m.month_start, cs.minimum_currency, cs.maximum_currency
                ORDER BY m.month_start
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("start", start)
                .addValue("end", end);

        return jdbcTemplate.query(sql, params, new DataClassRowMapper<>(TrendProjection.class));
    }

    public String getUserCurrency(UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT UPPER(currency_preference) FROM users WHERE id = :userId",
                new MapSqlParameterSource("userId", userId),
                String.class);
    }
}
