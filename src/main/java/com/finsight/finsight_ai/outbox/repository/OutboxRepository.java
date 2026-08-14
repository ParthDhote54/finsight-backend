package com.finsight.finsight_ai.outbox.repository;

import com.finsight.finsight_ai.outbox.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /*
     * the absolute core of our concurrent architecture.
     * this fetches a batch of pending events that are ready to be processed.
     * for update SKIP LOCKED guarantees that if multiple instances of this application
     * are running , they will never grab the same row, preventing double processing.
     */

    @Query(value = """
            SELECT * from outbox_events WHERE
            status = 'PENDING' AND next_attempt <= :now
            ORDER BY CREATED_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED""", //skip locked is written such that in multithread environment, A -> 1 to 5, B -> does not wait skip rows and process 6->10 rows

            nativeQuery = true)
    List<OutboxEvent> findPendingEventsForProcessing(@Param("now")LocalDateTime now, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = :status AND e.processedAt < :cutoff")
    int deleteByStatusAndProcessedAtBefore(@Param("status") String status, @Param("cutoff") LocalDate cutoff);
}
