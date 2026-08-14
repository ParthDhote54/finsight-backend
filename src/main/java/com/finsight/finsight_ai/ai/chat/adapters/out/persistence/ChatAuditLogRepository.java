package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatAuditLogRepository extends JpaRepository<ChatAuditLogEntity, UUID> {

    List<ChatAuditLogEntity> findByUserIdAndConversationIdOrderByCreatedAtAsc(UUID userId, UUID conversationId);

    List<ChatAuditLogEntity> findByFlaggedHallucinationTrueOrderByCreatedAtDesc();
}
