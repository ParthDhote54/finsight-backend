package com.finsight.finsight_ai.exception;

public class MixedCurrencyAggregationException extends RuntimeException {

    public MixedCurrencyAggregationException() {
        super("Authoritative aggregation across multiple currencies is not supported");
    }
}
