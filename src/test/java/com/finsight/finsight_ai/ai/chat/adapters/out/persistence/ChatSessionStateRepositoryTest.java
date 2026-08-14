package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import com.finsight.finsight_ai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChatSessionStateRepositoryTest {

    @Autowired
    private ChatSessionStateRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sameConversationIdRemainsIndependentForEachTenant() {
        UUID conversationId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        repository.saveAndFlush(session(userA, conversationId, "tenant A"));
        repository.saveAndFlush(session(userB, conversationId, "tenant B"));
        entityManager.clear();

        assertThat(repository.findByConversationIdAndUserId(conversationId, userA))
                .get()
                .extracting(ChatSessionStateEntity::getLastAnswerSummary)
                .isEqualTo("tenant A");
        assertThat(repository.findByConversationIdAndUserId(conversationId, userB))
                .get()
                .extracting(ChatSessionStateEntity::getLastAnswerSummary)
                .isEqualTo("tenant B");

        ChatSessionStateEntity tenantA = repository
                .findByConversationIdAndUserId(conversationId, userA)
                .orElseThrow();
        tenantA.setLastAnswerSummary("tenant A updated");
        repository.saveAndFlush(tenantA);
        entityManager.clear();

        assertThat(repository.findByConversationIdAndUserId(conversationId, userA))
                .get()
                .extracting(ChatSessionStateEntity::getLastAnswerSummary)
                .isEqualTo("tenant A updated");
        assertThat(repository.findByConversationIdAndUserId(conversationId, userB))
                .get()
                .extracting(ChatSessionStateEntity::getLastAnswerSummary)
                .isEqualTo("tenant B");
    }

    @Test
    void compositeRepositoryIdScopesDeletionToOneTenant() {
        UUID conversationId = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        repository.saveAndFlush(session(userA, conversationId, "tenant A"));
        repository.saveAndFlush(session(userB, conversationId, "tenant B"));

        repository.deleteById(new ChatSessionStateId(userA, conversationId));
        repository.flush();
        entityManager.clear();

        assertThat(repository.findByConversationIdAndUserId(conversationId, userA)).isEmpty();
        assertThat(repository.findByConversationIdAndUserId(conversationId, userB)).isPresent();
    }

    @Test
    void migrationDefinesTenantFirstCompositePrimaryKey() {
        List<String> primaryKeyColumns = jdbcTemplate.queryForList("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'chat_session_state'
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY kcu.ordinal_position
                """, String.class);

        assertThat(primaryKeyColumns).containsExactly("user_id", "conversation_id");
    }

    private static ChatSessionStateEntity session(UUID userId, UUID conversationId, String summary) {
        ChatSessionStateEntity session = new ChatSessionStateEntity();
        session.setUserId(userId);
        session.setConversationId(conversationId);
        session.setLastUserMessage("fixture question");
        session.setLastAnswerSummary(summary);
        return session;
    }
}
