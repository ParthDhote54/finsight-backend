package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogRepository;

import java.util.List;
import java.util.UUID;

public class ChatAuditLogEvaluationAuditSource implements EvaluationAuditSource {

    private final ChatAuditLogRepository repository;

    public ChatAuditLogEvaluationAuditSource(ChatAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ChatAuditLogEntity> auditsForConversation(UUID userId, UUID conversationId) {
        if (userId == null || conversationId == null) {
            return List.of();
        }
        return repository.findByUserIdAndConversationIdOrderByCreatedAtAsc(userId, conversationId);
    }
}
