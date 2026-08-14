package com.finsight.finsight_ai.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record TrendResponse(
        List<TrendDataPointDto> dataPoints,
        LocalDate startDate,
        LocalDate endDate,
        String currency
){
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private List<TrendDataPointDto> dataPoints;
        private LocalDate startDate;
        private LocalDate endDate;
        private String currency;

        public Builder dataPoints(List<TrendDataPointDto> dataPoints) { this.dataPoints = dataPoints; return this; }
        public Builder startDate(LocalDate startDate) { this.startDate = startDate; return this; }
        public Builder endDate(LocalDate endDate) { this.endDate = endDate; return this; }
        public Builder currency(String currency) { this.currency = currency; return this; }
        public TrendResponse build() { return new TrendResponse(dataPoints, startDate, endDate, currency); }
    }
}
