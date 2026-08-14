package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateEntity;
import com.finsight.finsight_ai.ai.chat.domain.DialogueState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests A-F: Verifies strict YES_NO semantics and stale-pending-state prevention.
 *
 * Rules:
 * - An affirmation executes pendingAction ONLY when lastAssistantAct in {OFFER,CONFIRMATION} AND expectedReplyType=YES_NO AND pendingAction != null.
 * - After execution, pendingAction/pendingSlot/expectedReplyType must be cleared (state → ANSWER).
 * - Subsequent affirmations MUST NOT re-execute the old action.
 * - "yes" with no active YES_NO returns __NATURAL_CONTINUATION__.
 * - "no" clears the pending action without executing.
 */
class DialogueStateConsumptionTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationalQueryResolver resolver = new ConversationalQueryResolver(fixedClock, objectMapper);

    /** Builds a session entity whose dialogueState JSON represents a YES/NO pending offer. */
    private ChatSessionStateEntity sessionWithPendingOffer(String pendingAction) throws Exception {
        DialogueState state = new DialogueState(
                "SPENDING_SUMMARY", null, null, "2026-08",
                null, null, null, null, null, null,
                pendingAction, null, "YES_NO", "OFFER", "Would you like to see spending by category?",
                Map.of()
        );
        String json = objectMapper.writeValueAsString(state);
        return ChatSessionStateEntity.builder()
                .lastToolName(null)
                .lastToolParams(null)
                .lastUserMessage("Where did most of my money go?")
                .dialogueState(json)
                .build();
    }

    /** Builds a session entity whose dialogueState represents a completed ANSWER (no pending action). */
    private ChatSessionStateEntity sessionWithAnsweredState() throws Exception {
        DialogueState state = DialogueState.answered();
        String json = objectMapper.writeValueAsString(state);
        return ChatSessionStateEntity.builder()
                .lastToolName("cashflow_summary")
                .lastToolParams("{\"month\":\"2026-08\"}")
                .lastUserMessage("income vs spend")
                .dialogueState(json)
                .build();
    }

    /**
     * TEST A: YES with active OFFER YES_NO → action executes once.
     * The returned MergedQuery should encode the action in normalizedMessage
     * and the returned dialogueState must have pendingAction cleared.
     */
    @Test
    void testA_affirmationExecutesPendingOfferOnce() throws Exception {
        ChatSessionStateEntity session = sessionWithPendingOffer("SHOW_SPENDING_BY_CATEGORY");

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("yes", session);

        // Action was encoded into the message
        assertThat(merged.normalizedMessage()).contains("spending breakdown by category");
        // pendingAction cleared in returned dialogueState
        assertThat(merged.dialogueState()).isNotNull();
        assertThat(merged.dialogueState().pendingAction()).isNull();
        assertThat(merged.dialogueState().expectedReplyType()).isEqualTo("NONE");
        assertThat(merged.dialogueState().lastAssistantAct()).isEqualTo("ANSWER");
    }

    /**
     * TEST B: Sending "yes" AGAIN after state is already ANSWER → no action resurrection.
     */
    @Test
    void testB_repeatedYesDoesNotRepeatAction() throws Exception {
        // State is already ANSWER (pendingAction=null, expectedReplyType=NONE)
        ChatSessionStateEntity session = sessionWithAnsweredState();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("yes", session);

        // Must return natural continuation, NOT spending by category
        assertThat(merged.normalizedMessage()).isEqualTo("__NATURAL_CONTINUATION__");
        assertThat(merged.dialogueState().pendingAction()).isNull();
        assertThat(merged.dialogueState().lastAssistantAct()).isEqualTo("ANSWER");
    }

    /**
     * TEST C: After completed finance answer (ANSWER state), "yes" must NOT trigger old clarification.
     */
    @Test
    void testC_yesAfterCompletedAnswerDoesNotResurrectClarification() throws Exception {
        // Simulate stale CLARIFICATION state that should have been cleared after answer
        DialogueState staleState = new DialogueState(
                "MONTH_COMPARISON", "MONTH_OVER_MONTH", null, "2026-08",
                "2026-07", "2026-08", null, null, null, "cashflow_summary",
                null, null, "NONE", "ANSWER", null, Map.of()
        );
        String json = objectMapper.writeValueAsString(staleState);
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .lastToolName("cashflow_summary")
                .lastToolParams("{}")
                .lastUserMessage("income vs spend")
                .dialogueState(json)
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("yes", session);

        assertThat(merged.normalizedMessage()).isEqualTo("__NATURAL_CONTINUATION__");
        assertThat(merged.dialogueState().pendingAction()).isNull();
    }

    /**
     * TEST D: "sure" (affirmative variant) with active YES_NO OFFER → executes action once.
     */
    @Test
    void testD_sureExecutesPendingActionOnce() throws Exception {
        ChatSessionStateEntity session = sessionWithPendingOffer("SHOW_SPENDING_BY_CATEGORY");

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("sure", session);

        assertThat(merged.normalizedMessage()).contains("spending breakdown by category");
        assertThat(merged.dialogueState().pendingAction()).isNull();
        assertThat(merged.dialogueState().lastAssistantAct()).isEqualTo("ANSWER");
    }

    /**
     * TEST E: "do it" (affirmative variant) with active YES_NO OFFER → executes action once.
     */
    @Test
    void testE_doItExecutesPendingActionOnce() throws Exception {
        ChatSessionStateEntity session = sessionWithPendingOffer("SHOW_SPENDING_BY_CATEGORY");

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("do it", session);

        assertThat(merged.normalizedMessage()).contains("spending breakdown by category");
        assertThat(merged.dialogueState().pendingAction()).isNull();
        assertThat(merged.dialogueState().lastAssistantAct()).isEqualTo("ANSWER");
    }

    /**
     * TEST F: "no" with active YES_NO → pendingAction cleared, no finance execution.
     */
    @Test
    void testF_negationClearsPendingActionWithoutExecution() throws Exception {
        ChatSessionStateEntity session = sessionWithPendingOffer("SHOW_SPENDING_BY_CATEGORY");

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("no", session);

        // No action message, no inherited tool
        assertThat(merged.normalizedMessage()).doesNotContain("spending by category");
        assertThat(merged.normalizedMessage()).doesNotContain("current month spending");
        assertThat(merged.inheritedToolName()).isNull();
        // State is cleared
        assertThat(merged.dialogueState().pendingAction()).isNull();
        assertThat(merged.dialogueState().lastAssistantAct()).isEqualTo("ANSWER");
    }

    /**
     * Additional: DialogueState.withAnswerState() correctly clears pending fields.
     */
    @Test
    void withAnswerStateClearsPendingFields() {
        DialogueState active = new DialogueState(
                "SPENDING_SUMMARY", null, null, "2026-08",
                null, null, null, null, null, "spend_by_category",
                "SHOW_SPENDING_BY_CATEGORY", "PERIOD", "YES_NO", "OFFER", "Would you like to see?",
                Map.of()
        );
        DialogueState answered = active.withAnswerState();

        assertThat(answered.pendingAction()).isNull();
        assertThat(answered.pendingSlot()).isNull();
        assertThat(answered.expectedReplyType()).isEqualTo("NONE");
        assertThat(answered.lastAssistantAct()).isEqualTo("ANSWER");
        assertThat(answered.lastAssistantQuestion()).isNull();
        // Preserved fields
        assertThat(answered.activeIntent()).isEqualTo("SPENDING_SUMMARY");
        assertThat(answered.period()).isEqualTo("2026-08");
    }
}
