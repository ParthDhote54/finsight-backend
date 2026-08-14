package com.finsight.finsight_ai.ai.chat.application;

import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogEntity;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists each chat audit in an independent short transaction. */
@Service
@RequiredArgsConstructor
public class ChatAuditPersistenceService {

    private final ChatAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(ChatAuditLogEntity auditLog) {
        repository.saveAndFlush(auditLog);
    }
}
