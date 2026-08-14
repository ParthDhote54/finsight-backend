package com.finsight.finsight_ai.ai.chat.adapters.out.ai;

import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class VertexAiEmbeddingAdapter implements EmbeddingPort {

    private final EmbeddingModel embeddingModel;

    public VertexAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
