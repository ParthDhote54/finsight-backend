package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogRepository;
import com.finsight.finsight_ai.ai.chat.application.IntentClassifier;
import com.finsight.finsight_ai.ai.chat.evaluation.seeder.EvaluationDemoDataSeeder;
import com.finsight.finsight_ai.ai.chat.ports.in.ChatUseCase;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.finsight.finsight_ai.ai.chat.evaluation.seeder.DemoDatasetGroundTruth.DEMO_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class Phase5FEvaluationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(Phase5FEvaluationBenchmarkTest.class);

    @Autowired
    private EvaluationDemoDataSeeder seeder;

    @Autowired
    private ChatUseCase chatUseCase;

    @Autowired
    private ChatAuditLogRepository chatAuditLogRepository;

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @MockBean
    private AIGateway aiGateway;

    @MockBean
    private ChatModelPort chatModelPort;

    @MockBean
    private EmbeddingPort embeddingPort;

    @BeforeEach
    void setUp() {
        seeder.seed();
    }

    @Test
    @DisplayName("5F: Execute 1x full benchmark evaluation across 65 grounded cases")
    void executePhase5FBenchmark() throws Exception {
        List<EvaluationCase> benchmarkCases = SyntheticEvaluationDataset.fullPhase5CDataset();
        assertThat(benchmarkCases).hasSize(65);

        EvaluationAuditSource auditSource = new ChatAuditLogEvaluationAuditSource(chatAuditLogRepository);
        EvaluationScorer scorer = new EvaluationScorer();
        EvaluationRunner runner = new EvaluationRunner(
                chatUseCase,
                auditSource,
                intentClassifier,
                objectMapper,
                scorer,
                () -> environment.getProperty("spring.ai.vertex.ai.gemini.chat.options.model", "gemini-2.5-flash-lite"),
                "1.0.0"
        );

        String runId = "evaluation_report_5f";
        log.info("Executing Phase 5F evaluation benchmark run: {} with {} cases", runId, benchmarkCases.size());

        List<EvaluationRunResult> results = runner.run(runId, DEMO_USER_ID, benchmarkCases);
        long evaluatedUniqueCases = results.stream().map(EvaluationRunResult::caseId).distinct().count();
        assertThat(evaluatedUniqueCases).isEqualTo(65);

        EvaluationMetricCalculator calculator = new EvaluationMetricCalculator();
        EvaluationMetrics metrics = calculator.calculate(results);

        log.info("Phase 5F Benchmark Metrics: Total Cases={}, System Success={}/{}, Model Selection={}/{}, Recovery={}, Unsupported Escapes={}",
                metrics.casesExecuted(),
                metrics.systemSuccessCount(), metrics.casesExecuted(),
                metrics.modelToolSelectionSuccessCount(), metrics.modelToolSelectionEligibleCases(),
                metrics.recoveryTriggeredCount(),
                metrics.unsupportedNumberEscapeCount());

        // Verify Critical Safety Invariant: unsupported numeric hallucinations reaching client = 0
        assertThat(metrics.unsupportedNumberEscapeCount()).isEqualTo(0);

        // Generate JSON & Markdown evaluation reports
        EvaluationReportGenerator reportGenerator = new EvaluationReportGenerator(objectMapper, calculator);
        Path targetReportDir = Paths.get("target", "evaluation-reports");
        EvaluationReportArtifact artifact = reportGenerator.writeReport(runId, results, targetReportDir);

        assertThat(artifact.jsonPath().toFile()).exists();
        assertThat(artifact.markdownPath().toFile()).exists();

        // Also write report to artifact directory if available
        String artifactEnvDir = "C:\\Users\\dpdho\\.gemini\\antigravity-ide\\brain\\1aba68f1-26d8-4250-ac91-bcbfb1b5a2d3";
        File artifactFolder = new File(artifactEnvDir);
        if (artifactFolder.exists()) {
            reportGenerator.writeReport(runId, results, artifactFolder.toPath());
        }
    }
}
