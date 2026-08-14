package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.domain.ToolExecutionResult;
import com.finsight.finsight_ai.ai.chat.domain.ToolSpec;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.ToolArgumentException;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Central registry that collects all FinanceTool beans and dispatches dynamic tool calls.
 */
@Component
public class ToolRegistry {

    private final Map<String, FinanceTool> tools;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<FinanceTool> toolList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, FinanceTool> registeredTools = new TreeMap<>();
        for (FinanceTool tool : toolList) {
            FinanceTool duplicate = registeredTools.putIfAbsent(tool.name(), tool);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate finance tool name: " + tool.name());
            }
        }
        this.tools = Collections.unmodifiableMap(registeredTools);
    }

    /**
     * Executes a tool by name with arguments parsed from Gemini.
     */
    public ToolExecutionResult execute(ToolCallRequest request) {
        if (!request.hasValidArgumentsJson()) {
            return error("INVALID_ARGUMENTS", null,
                    "Tool arguments must be a valid JSON object");
        }

        String toolName = request.toolName();
        if (toolName != null && toolName.contains(".")) {
            toolName = toolName.substring(toolName.lastIndexOf('.') + 1);
        }

        FinanceTool tool = tools.get(toolName);
        if (tool == null) {
            return error("UNKNOWN_TOOL", null, "The requested tool is not available: " + request.toolName());
        }

        try {
            rejectUnknownArguments(tool, request.arguments());
            FinanceToolResult result = tool.execute(request.arguments());
            String responseJson = objectMapper.writeValueAsString(result.data());
            return ToolExecutionResult.success(
                    responseJson, result.numericEvidence(), result.transactionEvidenceIds());
        } catch (ToolArgumentException exception) {
            return error(exception.code(), exception.field(), exception.getMessage());
        } catch (JsonProcessingException exception) {
            return systemError("TOOL_RESULT_SERIALIZATION_FAILED");
        } catch (RuntimeException exception) {
            return systemError("TOOL_EXECUTION_FAILED");
        }
    }

    /**
     * Converts registered FinanceTools into ToolSpec objects for ChatModelInput.
     */
    public List<ToolSpec> getToolSpecs() {
        return tools.values().stream()
                .map(tool -> new ToolSpec(tool.name(), tool.description(), serializeSchema(tool)))
                .toList();
    }

    private String serializeSchema(FinanceTool tool) {
        try {
            return objectMapper.writeValueAsString(tool.jsonSchema());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize schema for tool " + tool.name(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void rejectUnknownArguments(FinanceTool tool, Map<String, Object> arguments) {
        Object propertiesValue = tool.jsonSchema().get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)) {
            throw new IllegalStateException("Tool schema has no properties object: " + tool.name());
        }

        for (String argumentName : arguments.keySet()) {
            if (!properties.containsKey(argumentName)) {
                throw new ToolArgumentException(
                        "UNKNOWN_ARGUMENT", argumentName, "Unexpected argument: " + argumentName);
            }
        }
    }

    private ToolExecutionResult error(String code, String field, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("status", "ERROR");
        error.put("code", code);
        if (field != null) {
            error.put("field", field);
        }
        error.put("message", message);

        try {
            return ToolExecutionResult.modelCorrectableError(
                    objectMapper.writeValueAsString(error), code);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize structured tool error", exception);
        }
    }

    private ToolExecutionResult systemError(String code) {
        try {
            return ToolExecutionResult.systemError(
                    objectMapper.writeValueAsString(Map.of(
                            "status", "ERROR",
                            "code", code,
                            "message", "The financial data operation could not be completed")),
                    code);
        } catch (JsonProcessingException exception) {
            return ToolExecutionResult.systemError(
                    "{\"status\":\"ERROR\",\"code\":\"TOOL_EXECUTION_FAILED\"}",
                    "TOOL_EXECUTION_FAILED");
        }
    }
}
