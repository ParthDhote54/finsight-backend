package com.finsight.finsight_ai.analytics.controller;

import com.finsight.finsight_ai.analytics.dto.MonthlyCashFlowResponse;
import com.finsight.finsight_ai.analytics.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AnalyticsController analyticsController;

    private final UUID fakeUserId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(com.finsight.finsight_ai.security.UserPrincipal.class);
                    }
                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        com.finsight.finsight_ai.security.UserPrincipal principal = new com.finsight.finsight_ai.security.UserPrincipal();
                        principal.setUserId(fakeUserId);
                        return principal;
                    }
                })
                .build();
    }

    @Test
    void getCashFlowSummary_ShouldReturn200AndValidJson() throws Exception {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);

        MonthlyCashFlowResponse fakeResponse = new MonthlyCashFlowResponse(
                start, end, new BigDecimal("150000.00"), new BigDecimal("50000.00"),
                new BigDecimal("100000.00"), "INR"
        );

        Mockito.when(analyticsService.getCashFlowSummary(Mockito.any(), Mockito.eq(start), Mockito.eq(end)))
                .thenReturn(fakeResponse);

        mockMvc.perform(get("/api/v1/analytics/cashflow")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-07-31")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(150000.00))
                .andExpect(jsonPath("$.totalExpense").value(50000.00))
                .andExpect(jsonPath("$.netCashFlow").value(100000.00))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void getCashFlowSummary_ShouldReturn400_WhenMissingParams() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/cashflow")
                        .param("startDate", "2026-01-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
