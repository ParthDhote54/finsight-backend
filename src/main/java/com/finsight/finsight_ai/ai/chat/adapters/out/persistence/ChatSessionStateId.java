package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Tenant-scoped identity for one conversational session.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatSessionStateId implements Serializable {

    private UUID userId;
    private UUID conversationId;
}
