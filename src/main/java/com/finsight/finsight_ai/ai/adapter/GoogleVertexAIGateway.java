package com.finsight.finsight_ai.ai.adapter;

import com.finsight.finsight_ai.ai.AIGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleVertexAIGateway implements AIGateway {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GoogleVertexAIGateway.class);

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public GoogleVertexAIGateway(VertexAiGeminiChatModel vertexAiChatModel, EmbeddingModel embeddingModel) {
        this.chatClient = ChatClient.builder(vertexAiChatModel)
                .defaultSystem("""
                    You are an expert financial transaction classification system.
                    Assign the transaction to EXACTLY ONE category from the provided list.
                    Output must be ONLY the category name exactly as provided.
                    Do not output explanations, punctuation, quotes, markdown, slashes, or additional text.
                    """)
                .build();
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String categorize(String description, List<String> availableCategories) {
        log.debug("event=CALLING_GEMINI_CATEGORIZATION | description='{}'", description);

        StringBuilder categoriesListBuilder = new StringBuilder();
        for (String cat : availableCategories) {
            categoriesListBuilder.append("- ").append(cat).append("\n");
        }

        String prompt = String.format(
                "Categories:\n%s\nTransaction: %s\nOutput:",
                categoriesListBuilder,
                description
        );

        String aiResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        if (aiResponse == null || aiResponse.isBlank()) {
            throw new IllegalStateException("Vertex AI returned an empty categorization response");
        }

        return aiResponse.trim();
    }

    @Override
    public float[] generateEmbedding(String text) {
        log.debug("event=CALLING_VERTEX_EMBEDDING | textLength={}", text.length());
        return embeddingModel.embed(text);
    }
}