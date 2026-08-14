package com.finsight.finsight_ai.ai.chat;

import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogRepository;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateRepository;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelInput;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelOutput;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class ChatE2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatModelPort chatModelPort;

    @MockBean
    private com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort embeddingPort;

    @MockBean
    private AIGateway aiGateway;

    @Autowired
    private com.finsight.finsight_ai.repository.UserRepository userRepository;

    @Autowired
    private ChatAuditLogRepository chatAuditLogRepository;

    @Autowired
    private ChatSessionStateRepository chatSessionStateRepository;

    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID();
        principal = new UserPrincipal();
        principal.setUserId(userId);
        principal.setEmail("test@example.com");

        chatAuditLogRepository.deleteAll();
        chatSessionStateRepository.deleteAll();

        com.finsight.finsight_ai.entity.User user = new com.finsight.finsight_ai.entity.User();
        user.setId(userId);
        user.setEmail("test-" + userId + "@example.com");
        user.setPasswordHash("hashed_pw");
        user.setDisplayName("Test User");
        user.setCurrencyPreference("INR");
        userRepository.save(user);
        
        Mockito.when(embeddingPort.embed(Mockito.anyString())).thenReturn(new float[768]);
    }

    @Test
    void flagshipScenario_WhyDidMyFoodSpendingIncrease() throws Exception {
        // TURN 1: The user asks about food spending. The LLM invokes tools.
        String firstUserMessage = "Why did my food spending increase this month?";
        String requestJson1 = """
                {
                    "message": "%s"
                }
                """.formatted(firstUserMessage);

        // We mock the LLM port. It is called multiple times in a single turn.
        // First call: returns spending_delta_explainer
        ChatModelOutput output1_explainer = new ChatModelOutput(
                null,
                List.of(new ToolCallRequest("call_1", "spending_delta_explainer", Map.of("categoryOrGroup", "food", "periodA", "2026-06", "periodB", "2026-07"))),
                10, 10
        );
        // Second call: returns merchant_breakdown
        ChatModelOutput output1_breakdown = new ChatModelOutput(
                null,
                List.of(new ToolCallRequest("call_2", "merchant_breakdown", Map.of("categoryOrGroup", "food", "month", "2026-07"))),
                20, 20
        );
        // Third call: final text synthesizing the data
        String finalAnswerText = "Your food spending increased. (Simulated verified narrative).";
        ChatModelOutput output1_final = new ChatModelOutput(
                finalAnswerText,
                List.of(),
                30, 30
        );

        Mockito.when(chatModelPort.generate(Mockito.any(ChatModelInput.class)))
                .thenReturn(output1_explainer)
                .thenReturn(output1_breakdown)
                .thenReturn(output1_final);

        String response1Str = mockMvc.perform(post("/api/v1/chat")
                        .with(authentication(new TestingAuthenticationToken(principal, null, "ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson1))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(finalAnswerText))
                .andReturn().getResponse().getContentAsString();

        // Extract conversationId for follow-up
        // For simplicity, we just look it up in DB
        List<ChatAuditLogEntity> audits = chatAuditLogRepository.findAll();
        assertThat(audits).hasSize(1);
        ChatAuditLogEntity audit = audits.get(0);

        assertThat(audit.getUserId()).isEqualTo(userId);
        assertThat(audit.getUserMessage()).isEqualTo(firstUserMessage);
        assertThat(audit.getToolTurns()).isEqualTo(2); // Two tools were called
        assertThat(audit.getToolCalls()).contains("spending_delta_explainer").contains("merchant_breakdown");
        assertThat(audit.isFlaggedHallucination()).isFalse();

        UUID conversationId = audit.getConversationId();

        // Verify Session State is saved correctly
        var sessionOpt = chatSessionStateRepository.findByConversationIdAndUserId(conversationId, userId);
        assertThat(sessionOpt).isPresent();
        ChatSessionStateEntity session = sessionOpt.get();
        assertThat(session.getLastToolName()).isEqualTo("merchant_breakdown");

        // TURN 2: The user asks a follow up.
        String followUpMessage = "What about last month?";
        String requestJson2 = """
                {
                    "conversationId": "%s",
                    "message": "%s"
                }
                """.formatted(conversationId, followUpMessage);

        ChatModelOutput output2_final = new ChatModelOutput(
                "You spent differently last month.",
                List.of(),
                15, 15
        );
        Mockito.when(chatModelPort.generate(Mockito.any(ChatModelInput.class)))
                .thenReturn(output2_final);

        mockMvc.perform(post("/api/v1/chat")
                        .with(authentication(new TestingAuthenticationToken(principal, null, "ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You spent differently last month."));

        // Verify a second audit row was created
        List<ChatAuditLogEntity> finalAudits = chatAuditLogRepository.findAll();
        assertThat(finalAudits).hasSize(2);
    }
}
