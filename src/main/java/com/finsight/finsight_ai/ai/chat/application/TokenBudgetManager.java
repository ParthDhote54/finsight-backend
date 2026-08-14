package com.finsight.finsight_ai.ai.chat.application;

import com.finsight.finsight_ai.ai.chat.ports.out.VectorSearchPort.ScoredTransaction;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Manages token budget for context assembly before sending to the LLM.
 * Truncates retrieved context by removing lowest-similarity items first,
 * and never drops system instructions or tool-first directives.
 */
@Component
public class TokenBudgetManager {

    // Approximate tokens per character (conservative estimate for English + JSON)
    private static final double CHARS_PER_TOKEN = 4.0;
    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 3000;
    // Reserved for system prompt + tool specs + history (never trimmed)
    private static final int RESERVED_TOKENS = 800;

    /**
     * Trims retrieved transactions to fit within a token budget.
     * Preserves highest-similarity items; drops lowest-similarity first.
     *
     * @param retrieved      scored transactions ordered by similarity
     * @param toolResultsJson JSON strings from tool executions
     * @param maxTokens      optional max token budget (uses default if null or <= 0)
     * @return trimmed list of transactions that fit in the budget
     */
    public List<ScoredTransaction> trimToFit(List<ScoredTransaction> retrieved,
                                              List<String> toolResultsJson,
                                              Integer maxTokens) {
        int budget = (maxTokens != null && maxTokens > 0) ? maxTokens : DEFAULT_MAX_CONTEXT_TOKENS;
        int availableBudget = budget - RESERVED_TOKENS;

        // Subtract tool result token usage from available budget
        int toolTokens = toolResultsJson.stream()
                .mapToInt(json -> estimateTokens(json))
                .sum();
        availableBudget -= toolTokens;

        if (availableBudget <= 0 || retrieved == null || retrieved.isEmpty()) {
            return List.of(); // No room for RAG context
        }

        // Sort by similarity descending (keep best matches)
        List<ScoredTransaction> sorted = retrieved.stream()
                .sorted(Comparator.comparingDouble(ScoredTransaction::similarityScore).reversed())
                .toList();

        // Greedily add items until budget exhausted
        int usedTokens = 0;
        int keepCount = 0;
        for (ScoredTransaction tx : sorted) {
            int txTokens = estimateTransactionTokens(tx);
            if (usedTokens + txTokens > availableBudget) {
                break;
            }
            usedTokens += txTokens;
            keepCount++;
        }

        return sorted.subList(0, keepCount);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    private int estimateTransactionTokens(ScoredTransaction tx) {
        // Estimate based on typical serialized size of a transaction
        int chars = 36 + // UUID
                (tx.merchant() != null ? tx.merchant().length() : 0) +
                (tx.category() != null ? tx.category().length() : 0) +
                (tx.description() != null ? tx.description().length() : 0) +
                20; // amount + score + structure
        return (int) Math.ceil(chars / CHARS_PER_TOKEN);
    }
}
