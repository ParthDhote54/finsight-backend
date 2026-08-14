package com.finsight.finsight_ai.outbox.scheduler;

import com.finsight.finsight_ai.outbox.domain.OutboxEvent;
import com.finsight.finsight_ai.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OutboxFailureHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutboxFailureHandler.class);

    private static final int MAX_ATTEMPTS = 5;
    private final OutboxRepository outboxRepository;

    public OutboxFailureHandler(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void handleFailure(OutboxEvent event, Exception exception) {
        int nextAttemptCount = event.getAttemptCount() + 1;
        LocalDateTime now = LocalDateTime.now();

        event.setAttemptCount(nextAttemptCount);
        event.setNextAttempt(now);
        event.setErrorMessage(exception.getMessage());

        if (nextAttemptCount >= MAX_ATTEMPTS) {
            event.setStatus("FAILED");
            event.setNextAttempt(now);
            log.error("event=OUTBOX_EVENT_MAX_RETRIES_REACHED | event_id={} attempt_count={} status=FAILED error={}",
                    event.getId(), nextAttemptCount, exception.getMessage());
        } else {
            long delayMinutes = (long) Math.pow(2, nextAttemptCount - 1); // 1m, 2m, 4m, 8m
            LocalDateTime nextAttemptTime = now.plusMinutes(delayMinutes);

            event.setStatus("PENDING");
            event.setNextAttempt(nextAttemptTime);
            log.warn("event=OUTBOX_EVENT_SCHEDULED_RETRY | event_id={} attempt_count={} next_attempt={} error={}",
                    event.getId(), nextAttemptCount, nextAttemptTime, exception.getMessage());
        }

        outboxRepository.save(event);
    }
}