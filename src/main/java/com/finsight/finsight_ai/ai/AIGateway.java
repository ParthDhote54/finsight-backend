package com.finsight.finsight_ai.ai;


import java.util.List;
import java.util.UUID;

public interface AIGateway {


    String categorize(String description, List<String> availableCategories);

    float[] generateEmbedding(String text);
}
