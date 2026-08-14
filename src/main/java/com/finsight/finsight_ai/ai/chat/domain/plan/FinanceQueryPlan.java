package com.finsight.finsight_ai.ai.chat.domain.plan;

import java.util.List;

public record FinanceQueryPlan(
        Operation operation,
        Subject subject,
        Dimension dimension,
        Metric metric,
        List<String> entities,
        String period,
        String periodA,
        String periodB,
        Order order,
        Integer limit,
        String category,
        String merchant,
        String transactionType,
        boolean needsClarification,
        String missingSlot,
        Double confidence
) {
    public enum Operation {
        TOTAL, BREAKDOWN, COMPARE, RANK, LIST, EXPLAIN, UNKNOWN
    }

    public enum Subject {
        SPENDING, INCOME, TRANSACTION, SUBSCRIPTION, BALANCE, UNKNOWN
    }

    public enum Dimension {
        CATEGORY, MERCHANT, TRANSACTION, PERIOD, METRIC, UNKNOWN
    }

    public enum Metric {
        SUM_AMOUNT, COUNT, MIN_AMOUNT, MAX_AMOUNT, NET_CASHFLOW, DELTA, UNKNOWN
    }

    public enum Order {
        ASC, DESC, NONE
    }

    public FinanceQueryPlan {
        entities = entities == null ? List.of() : List.copyOf(entities);
        operation = operation == null ? Operation.UNKNOWN : operation;
        subject = subject == null ? Subject.UNKNOWN : subject;
        dimension = dimension == null ? Dimension.UNKNOWN : dimension;
        metric = metric == null ? Metric.UNKNOWN : metric;
        order = order == null ? Order.NONE : order;
    }

    public boolean isCategoryMinQuery() {
        return (operation == Operation.RANK || operation == Operation.TOTAL || operation == Operation.BREAKDOWN)
                && dimension == Dimension.CATEGORY
                && (metric == Metric.MIN_AMOUNT || order == Order.ASC);
    }

    public boolean isEntityComparison() {
        return operation == Operation.COMPARE && entities != null && entities.size() >= 2;
    }

    public boolean isLowestTransactionQuery() {
        return (operation == Operation.RANK || operation == Operation.LIST)
                && dimension == Dimension.TRANSACTION
                && (metric == Metric.MIN_AMOUNT || order == Order.ASC);
    }
}
