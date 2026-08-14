package com.finsight.finsight_ai.ai.chat.ports.out;

/**
 * Outbound port for embedding generation, keeping Spring AI / Vertex dependencies
 * out of the application core.
 */
public interface EmbeddingPort {
    
    /**
     * Generates a vector embedding for the given text.
     *
     * @param text the text to embed
     * @return the float array representing the vector
     */
    float[] embed(String text);
}
