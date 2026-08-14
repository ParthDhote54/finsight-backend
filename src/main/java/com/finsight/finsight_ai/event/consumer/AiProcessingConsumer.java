package com.finsight.finsight_ai.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.category.domain.CategoryConstants;
import com.finsight.finsight_ai.entity.Category;
import com.finsight.finsight_ai.outbox.domain.OutboxEvent;
import com.finsight.finsight_ai.repository.CategoryRepository;
import com.finsight.finsight_ai.transaction.application.port.in.TransactionQueryPort;
import com.finsight.finsight_ai.transaction.application.port.out.TransactionVectorPort;
import com.finsight.finsight_ai.transaction.domain.view.TransactionView;
import com.finsight.finsight_ai.transaction.port.TransactionCategoryUpdatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AiProcessingConsumer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiProcessingConsumer.class);

    private final TransactionQueryPort transactionQueryPort;
    private final TransactionCategoryUpdatePort categoryUpdatePort;
    private final TransactionVectorPort vectorPort;
    private final AIGateway aiGateWay;
    private final ObjectMapper objectMapper;
    private final CategoryRepository categoryRepository;

    public AiProcessingConsumer(
            TransactionQueryPort transactionQueryPort,
            TransactionCategoryUpdatePort categoryUpdatePort,
            TransactionVectorPort vectorPort,
            AIGateway aiGateWay,
            ObjectMapper objectMapper,
            CategoryRepository categoryRepository) {
        this.transactionQueryPort = transactionQueryPort;
        this.categoryUpdatePort = categoryUpdatePort;
        this.vectorPort = vectorPort;
        this.aiGateWay = aiGateWay;
        this.objectMapper = objectMapper;
        this.categoryRepository = categoryRepository;
    }

    @Value("${app.ai.default-category-name:" + CategoryConstants.OTHER + "}")
    private String defaultCategoryName;

    public void process(OutboxEvent event) throws Exception {
        JsonNode payload = objectMapper.readTree(event.getPayload());
        UUID userId = UUID.fromString(payload.get("userId").asText());
        UUID transactionId = UUID.fromString(payload.get("transactionId").asText());
        String rawDescription = payload.get("description").asText();

        UUID eventId = event.getId();
        int attemptCount = event.getAttemptCount();

        // Correlation context string
        String logContext = String.format("event_id=%s transaction_id=%s user_id=%s attempt_count=%d",
                eventId, transactionId, userId, attemptCount);

        // 0. Handle delete events explicitly without AI calls
        if ("TRANSACTION_DELETED".equalsIgnoreCase(event.getEventType())) {
            vectorPort.deleteVector(transactionId);
            log.info("event=TRANSACTION_VECTOR_DELETED | {}", logContext);
            return;
        }

        // 1. Fetch transaction view with ownership isolation
        Optional<TransactionView> transactionOpt = transactionQueryPort.getTransaction(transactionId, userId);

        if (transactionOpt.isEmpty()) {
            log.warn("event=TRANSACTION_NOT_FOUND | {}", logContext);
            return;
        }

        TransactionView view = transactionOpt.get();

        // 2. AI Categorization (only if category is unassigned)
        if (view.categoryId() == null) {
            List<Category> availableCategories = new ArrayList<>(categoryRepository.findByUserId(userId));
            availableCategories.addAll(categoryRepository.findByUserIdIsNull());

            if (availableCategories.isEmpty()) {
                availableCategories = categoryRepository.findAll();
            }

            List<String> categoryNames = availableCategories.stream()
                    .map(Category::getName)
                    .distinct()
                    .toList();

            long startTime = System.currentTimeMillis();
            String predictedCategoryName = aiGateWay.categorize(rawDescription, categoryNames);
            long latencyMs = System.currentTimeMillis() - startTime;

            log.info("event=AI_CALL_COMPLETED | category_returned='{}' latency_ms={} | {}",
                    predictedCategoryName, latencyMs, logContext);

            // FIX 1: Strict category resolution
            Optional<Category> matchedCategoryOpt = resolveCategory(predictedCategoryName, availableCategories);
            Category targetCategory;

            if (matchedCategoryOpt.isPresent()) {
                targetCategory = matchedCategoryOpt.get();
                log.info("event=CATEGORY_RESOLVED_EXACT | resolved_category='{}' | {}", targetCategory.getName(), logContext);
            } else {
                log.warn("event=CATEGORY_RESOLUTION_MISS | llm_output='{}' fallback_to='{}' | {}",
                        predictedCategoryName, defaultCategoryName, logContext);

                targetCategory = categoryRepository.findByName(defaultCategoryName)
                        .orElseGet(() -> categoryRepository.findByNameIgnoreCase("Other")
                                .orElseThrow(() -> new IllegalStateException("Configured fallback category missing from database: " + defaultCategoryName)));
            }

            categoryUpdatePort.updateCategoryIfNull(transactionId, targetCategory.getId());
        } else {
            log.info("event=CATEGORY_ALREADY_SET_SKIPPING_AI | category_id={} | {}", view.categoryId(), logContext);
        }

        // 3. FIX 5: SHA-256 Deduplication on Normalized Description
        String normalizedDescription = normalizeDescription(rawDescription);
        String contentHash = computeSha256(normalizedDescription);

        if (vectorPort.hasVectorForHash(transactionId, contentHash)) {
            log.info("event=VECTOR_HASH_EXISTS_SKIPPING | content_hash={} | {}", contentHash, logContext);
            return;
        }

        float[] embedding = aiGateWay.generateEmbedding(rawDescription);
        vectorPort.upsertVector(transactionId, userId, contentHash, embedding);
        log.info("event=TRANSACTION_VECTOR_UPSERTED | content_hash={} | {}", contentHash, logContext);
    }

    /**
     * FIX 1: Strict exact equality resolution. No fuzzy matching, contains(), or substring guessing.
     */
    private Optional<Category> resolveCategory(String llmOutput, List<Category> categories) {
        if (llmOutput == null) {
            return Optional.empty();
        }
        String cleanLlmOutput = llmOutput.trim();

        return categories.stream()
                .filter(c -> c.getName() != null && c.getName().trim().equalsIgnoreCase(cleanLlmOutput))
                .findFirst();
    }

    /**
     * FIX 5: Text normalization logic (trim, collapse spaces, lowercase).
     */
    public String normalizeDescription(String text) {
        if (text == null) return "";
        return text.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}