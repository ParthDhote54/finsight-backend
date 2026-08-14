package com.finsight.finsight_ai.outbox.scheduler;

import com.finsight.finsight_ai.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class OutboxCleanupService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OutboxCleanupService.class);

    private final OutboxRepository outboxRepository;

    public OutboxCleanupService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3:00 AM
    @Transactional
    public void cleanupProcessedEvents() {
        long startTime = System.currentTimeMillis();

        // Use LocalDateTime instead of Instant
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(7);

        int deletedCount = outboxRepository.deleteByStatusAndProcessedAtBefore("PROCESSED", LocalDate.from(retentionCutoff));
        long durationMs = System.currentTimeMillis() - startTime;

        log.info("event=OUTBOX_HOUSEKEEPING_COMPLETE | deleted_rows={} cutoff={} duration_ms={}",
                deletedCount, retentionCutoff, durationMs);
    }
}