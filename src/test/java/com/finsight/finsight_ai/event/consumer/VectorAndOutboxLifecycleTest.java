package com.finsight.finsight_ai.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.outbox.domain.OutboxEvent;
import com.finsight.finsight_ai.outbox.repository.OutboxRepository;
import com.finsight.finsight_ai.outbox.scheduler.OutboxFailureHandler;
import com.finsight.finsight_ai.outbox.scheduler.OutboxPoller;
import com.finsight.finsight_ai.repository.CategoryRepository;
import com.finsight.finsight_ai.transaction.application.port.in.TransactionQueryPort;
import com.finsight.finsight_ai.transaction.application.port.out.TransactionVectorPort;
import com.finsight.finsight_ai.transaction.domain.view.TransactionView;
import com.finsight.finsight_ai.transaction.port.TransactionCategoryUpdatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VectorAndOutboxLifecycleTest {

    private final TransactionQueryPort transactionQueryPort = mock(TransactionQueryPort.class);
    private final TransactionCategoryUpdatePort categoryUpdatePort = mock(TransactionCategoryUpdatePort.class);
    private final TransactionVectorPort vectorPort = mock(TransactionVectorPort.class);
    private final AIGateway aiGateway = mock(AIGateway.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiProcessingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AiProcessingConsumer(
                transactionQueryPort,
                categoryUpdatePort,
                vectorPort,
                aiGateway,
                objectMapper,
                categoryRepository
        );
    }

    @Test
    void transactionDeletedEventDeletesVectorWithoutAiCalls() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OutboxEvent deleteEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(transactionId)
                .eventType("TRANSACTION_DELETED")
                .payload("""
                        {
                            "userId": "%s",
                            "transactionId": "%s",
                            "description": "Starbucks Coffee"
                        }
                        """.formatted(userId, transactionId))
                .build();

        consumer.process(deleteEvent);

        verify(vectorPort, times(1)).deleteVector(transactionId);
        verify(aiGateway, never()).categorize(any(), any());
        verify(aiGateway, never()).generateEmbedding(any());
    }

    @Test
    void transactionCreatedUpsertsVector() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TransactionView view = new TransactionView(
                transactionId, userId, new BigDecimal("10.00"),
                "Starbucks", UUID.randomUUID(), LocalDate.now()
        );
        when(transactionQueryPort.getTransaction(transactionId, userId)).thenReturn(Optional.of(view));
        when(vectorPort.hasVectorForHash(any(), any())).thenReturn(false);
        when(aiGateway.generateEmbedding(any())).thenReturn(new float[768]);

        OutboxEvent createEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(transactionId)
                .eventType("TRANSACTION_CREATED")
                .payload("""
                        {
                            "userId": "%s",
                            "transactionId": "%s",
                            "description": "Starbucks Coffee"
                        }
                        """.formatted(userId, transactionId))
                .build();

        consumer.process(createEvent);

        verify(vectorPort, times(1)).upsertVector(eq(transactionId), eq(userId), any(), any());
    }

    @Test
    void outboxFailureHandlerSetsFailedStateWhenMaxRetriesReached() {
        OutboxRepository repository = mock(OutboxRepository.class);
        OutboxFailureHandler handler = new OutboxFailureHandler(repository);

        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .attemptCount(4) // 4 -> 5 on failure
                .status("PENDING")
                .build();

        handler.handleFailure(event, new RuntimeException("Vertex Timeout"));

        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getNextAttempt()).isNotNull();
        verify(repository, times(1)).save(event);
    }
}
