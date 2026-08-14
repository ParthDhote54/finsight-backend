package com.finsight.finsight_ai.ai.chat.ports.in;

import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;

import java.util.UUID;

/*
*Primary inbound port for the conversational AI engine.
*
*Defines the contract for processing multi-tenant, synchronous chat queries
* involving tool calling, RAG retrieval and citation validation.
 */
public interface ChatUseCase {
    /*
    *Processes an incoming chat query for a specific amount.
    *
    *@String userId The authenticated tenant ID (extracted strictly from JWT).
    * @Param request the incoming user prompt and optional session metadata.
    * @return ChatResponse containing the synthesized answer, citations, and execution metadata.
     */

    ChatResponse processChat(UUID userId, ChatRequest request);
}
