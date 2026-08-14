package com.finsight.finsight_ai.ai.chat.domain;

public record ToolSpec(
        String name,
        String description,
        String jsonSchemaParameters
) {}


