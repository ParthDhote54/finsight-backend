package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatSessionCleanupSchedulerTest {

    @Test
    void invokesRepositoryDeleteExpiredSessionsWithCutoff() {
        ChatSessionStateRepository repository = mock(ChatSessionStateRepository.class);
        ChatSessionCleanupScheduler scheduler = new ChatSessionCleanupScheduler(repository);
        ReflectionTestUtils.setField(scheduler, "ttlDays", 7);

        scheduler.cleanupExpiredSessions();

        verify(repository, times(1)).deleteExpiredSessions(any(LocalDateTime.class));
    }
}
