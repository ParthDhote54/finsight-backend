package com.finsight.finsight_ai.analytics.repository;


import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.analytics.repository.AnalyticsRepository;
import com.finsight.finsight_ai.analytics.projection.TrendProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataJpaTest
@Import({TestcontainersConfiguration.class, AnalyticsRepository.class})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public class AnalyticsRepositoryTest{
    @Autowired
    private AnalyticsRepository analyticsRepository;

    @Test
    public void monthlyTrend_shouldReturnZeroFilledMonths_ForEmptyDatabase() {
        UUID fakeUserID = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 1);

        //Act
        List<TrendProjection> result = analyticsRepository.monthlyTrend(fakeUserID, start, end);

        assertThat(result).hasSize(3);

        // Month 1
        assertThat(result.get(0).periodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(0).totalIncome()).isZero();
        assertThat(result.get(0).totalExpense()).isZero();

        // Month 3
        assertThat(result.get(2).periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    }
}
