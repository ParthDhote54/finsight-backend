package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateRepository;
import com.finsight.finsight_ai.ai.chat.application.validation.CitationValidatorService;
import com.finsight.finsight_ai.ai.chat.application.validation.NumericConsistencyValidator;
import com.finsight.finsight_ai.ai.chat.domain.*;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class RagRoutingTest {

    private final ChatModelPort chatModel = mock(ChatModelPort.class);
    private final ToolRegistry toolRegistry = mock(ToolRegistry.class);
    private final VectorSearchPort vectorSearch = mock(VectorSearchPort.class);
    private final EmbeddingPort embedding = mock(EmbeddingPort.class);
    private final TokenBudgetManager tokenBudget = mock(TokenBudgetManager.class);
    private final ChatAuditPersistenceService auditPersistence = mock(ChatAuditPersistenceService.class);
    private final ChatSessionStateRepository sessionRepository = mock(ChatSessionStateRepository.class);
    private ChatOrchestrationService service;

    @BeforeEach
    void setUp() {
        when(toolRegistry.getToolSpecs()).thenReturn(List.of());
        when(sessionRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());
        when(embedding.embed(any())).thenReturn(new float[768]);
        when(vectorSearch.similaritySearch(any(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(tokenBudget.trimToFit(any(), any(), any())).thenReturn(List.of());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        service = new ChatOrchestrationService(
                chatModel,
                toolRegistry,
                vectorSearch,
                embedding,
                tokenBudget,
                new CitationValidatorService(),
                new NumericConsistencyValidator(),
                new FinancialQueryDetector(),
                new IntentClassifier(),
                auditPersistence,
                sessionRepository,
                objectMapper);
    }

    @Test
    void structuredFinancialQuestionsDoNotCallVectorRetrieval() {
        when(chatModel.generate(any())).thenReturn(new ChatModelOutput("Direct answer", List.of(), 10, 10));

        // Question does not match FUZZY_RETRIEVAL pattern
        service.processChat(UUID.randomUUID(), new ChatRequest("How much did I spend on food in July?", null));

        verify(embedding, never()).embed(any());
        verify(vectorSearch, never()).similaritySearch(any(), any(), anyInt(), anyDouble());
    }

    @Test
    void fuzzySemanticQuestionsTriggerVectorRetrieval() {
        when(chatModel.generate(any())).thenReturn(new ChatModelOutput("Direct answer", List.of(), 10, 10));

        // Question matches FUZZY_RETRIEVAL pattern ("late-night junk food")
        service.processChat(UUID.randomUUID(), new ChatRequest("How much did I spend on late-night junk food?", null));

        verify(embedding, times(1)).embed(any());
        verify(vectorSearch, times(1)).similaritySearch(any(), any(), anyInt(), anyDouble());
    }
}
