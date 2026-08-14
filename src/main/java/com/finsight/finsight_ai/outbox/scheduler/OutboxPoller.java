package com.finsight.finsight_ai.outbox.scheduler;


import com.finsight.finsight_ai.event.consumer.AiProcessingConsumer;
import com.finsight.finsight_ai.outbox.domain.OutboxEvent;
import com.finsight.finsight_ai.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final AiProcessingConsumer aiProcessingConsumer;
    private final OutboxFailureHandler outboxFailureHandler;

    @Scheduled(fixedRate = 5000) //polls the database for pending outbox requests.
    public void pollOutboxEvents() {

        LocalDateTime now = LocalDateTime.now();

        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEventsForProcessing(now, 50);

        if(pendingEvents.isEmpty()) return;

        log.info("event = OUTBOX_EVENT_STARTED | count = {}", pendingEvents.size());

        for(OutboxEvent event : pendingEvents) {
            try{
                //1. Mark event as processing / lock if is your entity tracks it.
                event.setStatus("PROCESSING");
                outboxRepository.save(event);

                //2.delegate to your consumer.
                aiProcessingConsumer.process(event);

                //3.Mark as successfully completed.
                event.setStatus("PROCESSED");
                event.setProcessedAt(LocalDateTime.now());

                outboxRepository.save(event);
                log.info("event = OUTBOX_EVENT_PROCESSED | eventId = {}", event.getId());

            }
            catch (Exception e) {
                log.error("event=OUTBOX_EVENT_PROCESSING_ERROR | eventId={} | error={}", event.getId(), e.getMessage());

                // Call the new transaction boundary. Pass the ID, not the entity!
                outboxFailureHandler.handleFailure(event, e);
            }
        }
    }
}
