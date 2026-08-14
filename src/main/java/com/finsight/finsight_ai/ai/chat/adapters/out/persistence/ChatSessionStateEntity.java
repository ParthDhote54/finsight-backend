package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_session_state")
@IdClass(ChatSessionStateId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionStateEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "last_tool_name", length = 100)
    private String lastToolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_tool_params", columnDefinition = "jsonb")
    private String lastToolParams;

    @Column(name = "last_user_message", columnDefinition = "TEXT")
    private String lastUserMessage;

    @Column(name = "last_answer_summary", columnDefinition = "TEXT")
    private String lastAnswerSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dialogue_state", columnDefinition = "jsonb")
    private String dialogueState;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }
    public String getLastToolName() { return lastToolName; }
    public void setLastToolName(String lastToolName) { this.lastToolName = lastToolName; }
    public String getLastToolParams() { return lastToolParams; }
    public void setLastToolParams(String lastToolParams) { this.lastToolParams = lastToolParams; }
    public String getLastUserMessage() { return lastUserMessage; }
    public void setLastUserMessage(String lastUserMessage) { this.lastUserMessage = lastUserMessage; }
    public String getLastAnswerSummary() { return lastAnswerSummary; }
    public void setLastAnswerSummary(String lastAnswerSummary) { this.lastAnswerSummary = lastAnswerSummary; }
    public String getDialogueState() { return dialogueState; }
    public void setDialogueState(String dialogueState) { this.dialogueState = dialogueState; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
