package com.finsight.finsight_ai.analytics.service;

import com.finsight.finsight_ai.analytics.dto.*;
import com.finsight.finsight_ai.analytics.exception.InvalidDateRangeException;
import com.finsight.finsight_ai.analytics.projection.CategoryTotalProjection;
import com.finsight.finsight_ai.analytics.projection.TrendProjection;
import com.finsight.finsight_ai.analytics.projection.TypeTotalProjection;
import com.finsight.finsight_ai.analytics.repository.AnalyticsRepository;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.exception.MixedCurrencyAggregationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Enforces this service as Read-Only at the DB level
@Slf4j
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private static final int MAX_MONTHS_RANGE = 24;

    public MonthlyCashFlowResponse getCashFlowSummary(UUID userId, LocalDate start, LocalDate end) {
        validDateRange(start, end);
        log.info("event = FETCH_CASH_FLOW | userId = {} | start = {} | end = {}", userId, start, end);

        // Fetching data purely through the repository method
        List<TypeTotalProjection> projectionList = analyticsRepository.sumByTypeInRange(userId, start, end);
        String currency = projectionList.isEmpty()
                ? analyticsRepository.getUserCurrency(userId)
                : requireSingleCurrency(
                        projectionList.get(0).minimumCurrency(),
                        projectionList.get(0).maximumCurrency());

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        for (TypeTotalProjection projection : projectionList) {
            if (projection.type() == TransactionType.INCOME) {
                income = projection.total();
            } else if (projection.type() == TransactionType.EXPENSE) {
                expense = projection.total();
            }
        }

        BigDecimal netCashFlow = income.subtract(expense);

        return MonthlyCashFlowResponse.builder()
                .netCashFlow(netCashFlow)
                .periodStart(start)
                .periodEnd(end)
                .totalIncome(income)
                .totalExpense(expense)
                .currency(currency)
                .build();
    }

    public void validDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new InvalidDateRangeException("Start and end dates are required.");
        }

        if (start.isAfter(end)) {
            log.warn("event = INVALID_DATE_RANGE | reason = start_after_end | start = {} | end = {}", start, end);
            throw new InvalidDateRangeException("Start date cannot be after end date.");
        }

        long monthCount = ChronoUnit.MONTHS.between(
                start.withDayOfMonth(1), end.withDayOfMonth(1)) + 1;
        if (monthCount > MAX_MONTHS_RANGE) {
            log.warn("event = INVALID_DATE_RANGE | reason = exceeds_max_months | requested = {} | max = {}", monthCount, MAX_MONTHS_RANGE);
            throw new InvalidDateRangeException("Date range cannot exceed " + MAX_MONTHS_RANGE + " months.");
        }
    }

    public ExpenseBreakDownResponse getExpenseBreakDown(UUID userId, LocalDate start, LocalDate end) {
        validDateRange(start, end);
        log.info("event = FETCH_EXPENSE_BREAKDOWN | userId = {} | start = {} | end = {}", userId, start, end);

        List<CategoryTotalProjection> projectionList = analyticsRepository.sumByCategoryInRange(userId, TransactionType.EXPENSE, start, end);
        String currency = projectionList.isEmpty()
                ? analyticsRepository.getUserCurrency(userId)
                : requireSingleCurrency(
                        projectionList.get(0).minimumCurrency(),
                        projectionList.get(0).maximumCurrency());

        BigDecimal totalExpenses = projectionList.stream()
                .map(CategoryTotalProjection::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryPercentageDto> percentageDtoList = projectionList.stream()
                .map(proj -> {
                    BigDecimal percentage = BigDecimal.ZERO;
                    if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                        percentage = proj.total().multiply(new BigDecimal(100))
                                .divide(totalExpenses, 2, RoundingMode.HALF_UP);
                    }
                    return CategoryPercentageDto.builder()
                            .categoryId(proj.categoryId())
                            .categoryName(proj.categoryName())
                            .percentage(percentage)
                            .amount(proj.total())
                            .build();
                }).toList();

        return ExpenseBreakDownResponse.builder()
                .breakDown(percentageDtoList)
                .totalExpenses(totalExpenses)
                .start(start)
                .end(end)
                .currency(currency)
                .build();
    }

    public TrendResponse getMonthlyTrend(UUID userId, LocalDate start, LocalDate end) {
        validDateRange(start, end);
        log.info("event = FETCH_TREND | userId = {} | start = {} | end = {}", userId, start, end);

        List<TrendProjection> projections = analyticsRepository.monthlyTrend(userId, start, end);
        String currency = projections.isEmpty()
                ? analyticsRepository.getUserCurrency(userId)
                : requireSingleCurrency(
                        projections.get(0).minimumCurrency(),
                        projections.get(0).maximumCurrency());

        List<TrendDataPointDto> dataPointDto = projections.stream()
                .<TrendDataPointDto>map(trendProjection -> {
                    BigDecimal net = trendProjection.totalIncome().subtract(trendProjection.totalExpense());
                    return TrendDataPointDto.builder()
                            .totalIncome(trendProjection.totalIncome())
                            .totalExpense(trendProjection.totalExpense())
                            .periodStart(trendProjection.periodStart())
                            .net(net)
                            .build();
                })
                .toList();

        return TrendResponse.builder()
                .endDate(end)
                .startDate(start)
                .currency(currency)
                .dataPoints(dataPointDto)
                .build();
    }

    private static String requireSingleCurrency(String minimumCurrency, String maximumCurrency) {
        if (minimumCurrency == null || maximumCurrency == null
                || !minimumCurrency.equalsIgnoreCase(maximumCurrency)) {
            throw new MixedCurrencyAggregationException();
        }
        return minimumCurrency.toUpperCase(java.util.Locale.ROOT);
    }
}
