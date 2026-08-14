package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;

import java.util.List;
import java.util.UUID;

public interface EvaluationAuditSource {

    List<ChatAuditLogEntity> auditsForConversation(UUID userId, UUID conversationId);
}
