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

class HumanAcceptanceRegressionTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationalQueryResolver resolver = new ConversationalQueryResolver(fixedClock, objectMapper);

    @Test
    void test1_allFillsCategoryScopeWhenCategoryComparisonActive() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, null, null,
                null, null, "CATEGORY_SCOPE", "CHOICE", "CLARIFICATION", "Which categories?",
                Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("all", session);

        assertThat(merged.dialogueState()).isNotNull();
        assertThat(merged.dialogueState().categoryScope()).isEqualTo("ALL");
    }

    @Test
    void test2_currentFillsPendingPeriodSlot() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "ALL", null,
                "spend_by_category", null, "PERIOD", "PERIOD", "CLARIFICATION", "Which month?",
                Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("current", session);

        assertThat(merged.resolvedMonth()).isEqualTo("2026-08");
        assertThat(merged.inheritedToolName()).isEqualTo("spend_by_category");
        assertThat(merged.normalizedMessage()).contains("spending breakdown by category for 2026-08");
    }

    @Test
    void test3_thisMonthFillsPendingPeriodSlot() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "ALL", null,
                "spend_by_category", null, "PERIOD", "PERIOD", "CLARIFICATION", "Which time period?",
                Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("this month", session);

        assertThat(merged.resolvedMonth()).isEqualTo("2026-08");
        assertThat(merged.inheritedToolName()).isEqualTo("spend_by_category");
    }

    @Test
    void test4_byCategoryPreservesCategorySpendingIntent() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "ALL", null,
                "spend_by_category", null, null, "NONE", "ANSWER", null,
                Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("by category", session);

        assertThat(merged.dialogueState().activeIntent()).isEqualTo("CATEGORY_SPENDING");
    }

    @Test
    void test5_provideBreakdownPreservesCategorySpendingIntent() throws Exception {
        DialogueState state = new DialogueState(
                "CATEGORY_SPENDING", null, null, null,
                null, null, null, "ALL", null,
                "spend_by_category", null, null, "NONE", "ANSWER", null,
                Map.of()
        );
        ChatSessionStateEntity session = ChatSessionStateEntity.builder()
                .dialogueState(objectMapper.writeValueAsString(state))
                .build();

        ConversationalQueryResolver.MergedQuery merged = resolver.mergeContext("provide breakdown", session);

        assertThat(merged.dialogueState().activeIntent()).isEqualTo("CATEGORY_SPENDING");
    }
}
