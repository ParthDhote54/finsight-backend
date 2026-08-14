package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionStateRepository extends JpaRepository<ChatSessionStateEntity, ChatSessionStateId> {

    Optional<ChatSessionStateEntity> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatSessionStateEntity s WHERE s.updatedAt < :cutoff")
    int deleteExpiredSessions(LocalDateTime cutoff);
}
