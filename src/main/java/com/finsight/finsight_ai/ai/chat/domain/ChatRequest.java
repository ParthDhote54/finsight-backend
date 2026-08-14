package com.finsight.finsight_ai.ai.chat.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatRequest(

        @NotBlank(message = "message prompt cannot be blank")
        @Size(max = 2000, message = "message prompt cannot exceed 2000 characters")
        String message,

        UUID conversationId
) {
    /*
    compact constructor to ensure clean state with whitespace normalization.
     */


    public ChatRequest{
        if(message != null) {
            message = message.trim();
        }
    }
}
