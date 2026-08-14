package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.persistence.ChatSessionStateEntity;
import com.finsight.finsight_ai.ai.chat.domain.DialogueState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ConversationalQueryResolverTest {

    private ConversationalQueryResolver resolver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Fixed clock at 2026-08-11T10:00:00Z
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T10:00:00Z"), ZoneId.of("UTC"));
        resolver = new ConversationalQueryResolver(clock, objectMapper);
    }

    @Test
    void testCurrentFillsPendingPeriodSlot() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "ALL", null,
                null, null, "PERIOD", "PERIOD", "CLARIFICATION", "Which month?", java.util.Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("current", session);
        assertEquals("2026-08", merged.resolvedMonth());
        assertEquals("spend_by_category", merged.inheritedToolName());
        assertEquals("CATEGORY_SPENDING", merged.dialogueState().activeIntent());
        assertNull(merged.dialogueState().pendingSlot());
    }

    @Test
    void testThisMonthFillsPendingPeriodSlot() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "ALL", null,
                null, null, "PERIOD", "PERIOD", "CLARIFICATION", "Which month?", java.util.Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("this month", session);
        assertEquals("2026-08", merged.resolvedMonth());
        assertEquals("spend_by_category", merged.inheritedToolName());
    }

    @Test
    void testAllFillsCategoryScopeAndAsksForPeriodIfMissing() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "UNSPECIFIED", null,
                null, null, "PERIOD", "PERIOD", "CLARIFICATION", "Which month?", java.util.Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("all", session);
        assertTrue(merged.normalizedMessage().startsWith("__CLARIFICATION__:"));
        assertEquals("ALL", merged.dialogueState().categoryScope());
        assertEquals("PERIOD", merged.dialogueState().pendingSlot());
    }

    @Test
    void testByCategoryPreservesIntent() {
        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("by category", null);
        assertTrue(merged.normalizedMessage().startsWith("__CLARIFICATION__:"));
        assertEquals("CATEGORY_SPENDING", merged.dialogueState().activeIntent());
        assertEquals("PERIOD", merged.dialogueState().pendingSlot());
    }

    @Test
    void testProvideBreakdownPreservesIntent() {
        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("provide breakdown", null);
        assertEquals("2026-08", merged.resolvedMonth());
        assertEquals("spend_by_category", merged.inheritedToolName());
        assertEquals("CATEGORY_SPENDING", merged.dialogueState().activeIntent());
    }

    @Test
    void testWhereDidMostOfMyMoneyGoUsesSpendByCategory() {
        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("Where did most of my money go this month?", null);
        assertEquals("2026-08", merged.resolvedMonth());
        assertEquals("spend_by_category", merged.inheritedToolName());
        assertEquals("CATEGORY_SPENDING", merged.dialogueState().activeIntent());
    }

    @Test
    void testImperfectEnglishNormalization() {
        ConversationalQueryResolver.MergedQuery m1 = resolver.mergeContext("where money gone this month", null);
        assertEquals("2026-08", m1.resolvedMonth());
        assertEquals("spend_by_category", m1.inheritedToolName());

        ConversationalQueryResolver.MergedQuery m2 = resolver.mergeContext("were did my mony go current month", null);
        assertEquals("2026-08", m2.resolvedMonth());
        assertEquals("spend_by_category", m2.inheritedToolName());

        ConversationalQueryResolver.MergedQuery m3 = resolver.mergeContext("comapre category spend current", null);
        assertEquals("2026-08", m3.resolvedMonth());
        assertEquals("spend_by_category", m3.inheritedToolName());

        ConversationalQueryResolver.MergedQuery m4 = resolver.mergeContext("show all spend category current", null);
        assertEquals("2026-08", m4.resolvedMonth());
        assertEquals("spend_by_category", m4.inheritedToolName());

        ConversationalQueryResolver.MergedQuery m5 = resolver.mergeContext("were dis my mony go standard current month", null);
        assertEquals("2026-08", m5.resolvedMonth());
        assertEquals("spend_by_category", m5.inheritedToolName());
    }

    @Test
    void testWhyFollowUpReturnsClarificationWithContext() throws Exception {
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .lastUserMessage("which category had lowest spending for this month")
                .dialogueState(objectMapper.writeValueAsString(DialogueState.empty()))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("why ??", session);
        assertTrue(merged.normalizedMessage().startsWith("__CLARIFICATION__:"));
        assertTrue(merged.normalizedMessage().contains("which category had lowest spending for this month"));
    }

    @Test
    void testCurrentYearFollowUpInheritsContext() throws Exception {
        DialogueState state = new DialogueState(
                "LOWEST_TRANSACTION", null, "MIN_AMOUNT", "2026-08",
                null, null, null, null, null,
                "lowest_transaction", null, null, "NONE", "ANSWER", null, java.util.Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .lastUserMessage("lowest transaction in this month")
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("current year", session);
        assertEquals("2026-08", merged.resolvedMonth());
        assertEquals("lowest_transaction", merged.inheritedToolName());
    }
}
