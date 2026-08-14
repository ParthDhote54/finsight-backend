package com.finsight.finsight_ai.ai.chat.adapters.in.web;

import com.finsight.finsight_ai.ai.chat.domain.ChatRequest;
import com.finsight.finsight_ai.ai.chat.domain.ChatResponse;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import com.finsight.finsight_ai.ai.chat.ports.in.ChatUseCase;

import com.finsight.finsight_ai.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatUseCase chatUseCase;

    /**
     * Synchronous conversational endpoint.
     *
     * @param principal : the authenticated user principal extracted securely from JWT.
     * @param request   : The incoming validated chat request body.
     * @return ResponseEntity<ChatResponse> the structured response with HTTP 200.
     */
    @PostMapping
    public ResponseEntity<ChatResponse> processChat(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChatRequest request
    ) {
        // Enforce secure tenant extraction from JWT securityContext.
        UUID userId = principal.getUserId();

        try {
            // 1. Lock userId into ThreadLocal vault for secure downstream tool calls
           TenantContext.set(userId);

            // 2. Delegate to Inbound Port.
            ChatResponse chatResponse = chatUseCase.processChat(userId, request);

            return ResponseEntity.ok(chatResponse);
        } finally {
            // 3. Prevent ThreadLocal memory leaks in worker thread pool
            TenantContext.clear();
        }
    }
}