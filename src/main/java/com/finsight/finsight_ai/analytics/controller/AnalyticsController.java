package com.finsight.finsight_ai.analytics.controller;

import com.finsight.finsight_ai.analytics.dto.ExpenseBreakDownResponse;
import com.finsight.finsight_ai.analytics.dto.MonthlyCashFlowResponse;
import com.finsight.finsight_ai.analytics.dto.TrendResponse;
import com.finsight.finsight_ai.analytics.service.AnalyticsService;

// ⭐ THE CRITICAL IMPORT: This MUST point to YOUR custom class from Phase 1, NOT a built-in Java class.


import com.finsight.finsight_ai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/cashflow")
    public ResponseEntity<MonthlyCashFlowResponse> getCashFlowSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // Assuming your custom UserPrincipal has a .getId() method that returns a UUID.
        // If it returns a String, change this to: UUID.fromString(principal.getId())
        UUID userId = principal.getUserId();

        MonthlyCashFlowResponse response = analyticsService.getCashFlowSummary(userId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expense/breakdown")
    public ResponseEntity<ExpenseBreakDownResponse> getExpenseBreakDown(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        UUID userId = principal.getUserId();

        return ResponseEntity.ok(analyticsService.getExpenseBreakDown(userId, startDate, endDate));
    }

    @GetMapping("/trend")
    public ResponseEntity<TrendResponse> getMonthlyTrend(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        UUID userId = principal.getUserId();
        return ResponseEntity.ok(analyticsService.getMonthlyTrend(userId, startDate, endDate));
    }
}