package com.finsight.finsight_ai.ai.chat.domain.tools;

import java.util.Map;

/**
 * Core interface for all AI domain tools in FinSight.
 * Keeps tools strongly decoupled from specific Spring AI infrastructure bindings.
 */
public interface FinanceTool {

    /**
     * Unique tool identifier matched during LLM function calling.
     */
    String name();

    /**
     * Clear narrative description explaining to Gemini when and how to trigger this tool.
     */
    String description();

    /**
     * JSON Schema specification defining expected parameters.
     */
    Map<String, Object> jsonSchema();

    /**
     * Executes validated arguments and returns data plus typed financial evidence.
     */
    FinanceToolResult execute(Map<String, Object> args);
}
