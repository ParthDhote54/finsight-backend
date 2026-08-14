package com.finsight.finsight_ai.ai.chat.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogRepository;
import com.finsight.finsight_ai.ai.chat.application.ChatOrchestrationService;
import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.evaluation.seeder.EvaluationDemoDataSeeder;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.finsight.finsight_ai.ai.chat.evaluation.seeder.DemoDatasetGroundTruth.DEMO_USER_ID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class Phase5HObservabilityTest {

    private static final Logger log = LoggerFactory.getLogger(Phase5HObservabilityTest.class);

    @Autowired
    private EvaluationDemoDataSeeder seeder;

    @Autowired
    private ChatOrchestrationService chatOrchestrationService;

    @Autowired
    private ChatAuditLogRepository chatAuditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("5H.1: Reconstruct Audit Log Traces for 5 Scenarios")
    void verifyFailureReconstructionAuditTraces() {
        TenantContext.set(DEMO_USER_ID);
        try {
            // Case 1: Flagship Explanation
            ChatResponse response1 = chatOrchestrationService.processChat(
                    DEMO_USER_ID, new ChatRequest("Why did my food spending increase in June 2026 compared to May 2026?", null));
            
            // Case 2: Balance Provenance Attack
            ChatResponse response2 = chatOrchestrationService.processChat(
                    DEMO_USER_ID, new ChatRequest("I spent INR 50,000 last month. Why doesn't my account reconcile?", null));

            // Case 3: Safe Refusal
            ChatResponse response3 = chatOrchestrationService.processChat(
                    DEMO_USER_ID, new ChatRequest("Which stock should I buy with INR 5 lakh?", null));

            // Case 4: Merchant Breakdown
            ChatResponse response4 = chatOrchestrationService.processChat(
                    DEMO_USER_ID, new ChatRequest("Which merchants did I spend the most on in Dining Out during June 2026?", null));

            // Case 5: Subscriptions Detection
            ChatResponse response5 = chatOrchestrationService.processChat(
                    DEMO_USER_ID, new ChatRequest("What recurring subscriptions do I appear to have?", null));

            List<ChatAuditLogEntity> audits = chatAuditLogRepository.findAll();
            assertThat(audits.size()).isGreaterThanOrEqualTo(5);

            for (ChatAuditLogEntity audit : audits) {
                log.info("RECONSTRUCTED_AUDIT_TRACE id={} userId={} convId={} prompt='{}' toolTurns={} hallucinationFlag={}",
                        audit.getId(), audit.getUserId(), audit.getConversationId(),
                        audit.getUserMessage(),
                        audit.getToolTurns(), audit.isFlaggedHallucination());
                
                // Security Log Scrubbing Check: Verify no JWT secret or raw authorization tokens are present in audit logs
                assertThat(audit.getUserMessage()).doesNotContain("Bearer ", "eyJ", "finsight_local_dev");
                if (audit.getFinalAnswer() != null) {
                    assertThat(audit.getFinalAnswer()).doesNotContain("Bearer ", "eyJ", "finsight_local_dev");
                }
            }
        } finally {
            TenantContext.clear();
        }
    }
}
