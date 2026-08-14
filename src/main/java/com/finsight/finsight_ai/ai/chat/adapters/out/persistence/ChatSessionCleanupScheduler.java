package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ChatSessionCleanupScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatSessionCleanupScheduler.class);

    private final ChatSessionStateRepository repository;

    public ChatSessionCleanupScheduler(ChatSessionStateRepository repository) {
        this.repository = repository;
    }

    @Value("${app.chat.session-ttl-days:7}")
    private int ttlDays;

    @Scheduled(cron = "${app.chat.session-cleanup-cron:0 0 2 * * ?}")
    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(ttlDays);
        int deletedCount = repository.deleteExpiredSessions(cutoff);
        if (deletedCount > 0) {
            log.info("event=CHAT_SESSION_CLEANUP | deleted_sessions={}", deletedCount);
        }
    }
}
