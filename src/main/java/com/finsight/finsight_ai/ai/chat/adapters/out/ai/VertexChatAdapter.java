package com.finsight.finsight_ai.ai.chat.adapters.out.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelInput;
import com.finsight.finsight_ai.ai.chat.domain.ChatModelOutput;
import com.finsight.finsight_ai.ai.chat.domain.ChatTurn;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.finsight.finsight_ai.ai.chat.domain.Role.*;
@Component
@RequiredArgsConstructor
public class VertexChatAdapter implements ChatModelPort{

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    @Override
    public ChatModelOutput generate(ChatModelInput input) {
        Objects.requireNonNull(input, "ChatModelInput cannot be empty");

        List<Message> springAiMessages = new ArrayList<>();
        StringBuilder combinedSystemPrompt = new StringBuilder();

        //1. Append System prompt.
        if (input.systemPrompt() != null && !input.systemPrompt().isBlank()) {
            combinedSystemPrompt.append(input.systemPrompt()).append("\n\n");
        }

        //2.Map conversation history.
        for (ChatTurn turn : input.history()) {
            if (turn.role().equals(USER)) {
                springAiMessages.add(new UserMessage(turn.content()));
            } else if (turn.role().equals(ASSISTANT)) {
                springAiMessages.add(toAssistantMessage(turn));
            } else if (turn.role().equals(SYSTEM)) {
                combinedSystemPrompt.append(turn.content()).append("\n\n");
            } else if (turn.role().equals(TOOL)) {
                springAiMessages.add(toToolResponseMessage(turn));
            }
        }
        
        if (!combinedSystemPrompt.isEmpty()) {
            springAiMessages.add(0, new SystemMessage(combinedSystemPrompt.toString().trim()));
        }

        //3. Append current user message
        if (input.userMessage() != null && !input.userMessage().isBlank()) {
            springAiMessages.add(new UserMessage(input.userMessage()));
        }

        List<ToolCallback> toolCallbacks = input.availableTools().stream()
                .map(this::toToolCallback)
                .toList();
        VertexAiGeminiChatOptions options = VertexAiGeminiChatOptions.builder()
                .candidateCount(1)
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();

        //4 construct Spring AI prompt with provider-visible tool definitions.
        Prompt prompt = new Prompt(springAiMessages, options);

        //5 Invoke Google vertex Ai using spring AI.
        ChatResponse response = chatModel.call(prompt);
        if(response == null || response.getResult() == null) {
            throw new IllegalStateException("Vertex AI returned no generation");
        }

        //6. Extract Generation & Tool calls.
        Generation generation = response.getResult();
        AssistantMessage outputMessage = generation.getOutput();

        String textAnswer = outputMessage.getText() != null ? outputMessage.getText() : "";
        List<ToolCallRequest>toolCalls = extractToolCalls(outputMessage);


        //7. Extract usage metadata.
        int promptTokens = 0;
        int completionTokens = 0;
        if(response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            promptTokens = response.getMetadata().getUsage().getPromptTokens();
            completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        }

        return new ChatModelOutput(textAnswer, toolCalls, promptTokens, completionTokens);
    }

    private List<ToolCallRequest> extractToolCalls(AssistantMessage assistantMessage) {
        List<ToolCallRequest> requests = new ArrayList<>();

        if(assistantMessage.getToolCalls() != null) {
            for(AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                ParsedArguments parsed = parseArgumentsJson(toolCall.arguments());
                String callId = toolCall.id();
                if (callId == null || callId.isBlank()) {
                    callId = "generated-" + UUID.randomUUID();
                }
                requests.add(new ToolCallRequest(
                        callId,
                        toolCall.name(),
                        parsed.arguments(),
                        toolCall.arguments(),
                        parsed.error()
                ));
            }
        }

        return requests;
    }

    private ParsedArguments parseArgumentsJson(String rawJson) {
        if(rawJson == null || rawJson.isBlank()) {
            return new ParsedArguments(Map.of(), "Tool arguments were empty");
        }
        try{
            Map<String, Object> arguments = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {
            });
            if (arguments == null) {
                return new ParsedArguments(Map.of(), "Tool arguments were not a JSON object");
            }
            return new ParsedArguments(arguments, null);
        }
        catch (Exception e) {
            return new ParsedArguments(Map.of(), "Tool arguments were not a valid JSON object");
        }
    }

    private AssistantMessage toAssistantMessage(ChatTurn turn) {
        List<AssistantMessage.ToolCall> calls = turn.toolCalls().stream()
                .map(call -> new AssistantMessage.ToolCall(
                        call.callId(),
                        "function",
                        call.toolName(),
                        argumentsJson(call)))
                .toList();
        return new AssistantMessage(turn.content(), Map.of(), calls);
    }

    private ToolResponseMessage toToolResponseMessage(ChatTurn turn) {
        List<ToolResponseMessage.ToolResponse> responses = turn.toolResults().stream()
                .map(result -> new ToolResponseMessage.ToolResponse(
                        result.callId(), result.toolName(), result.responseJson()))
                .toList();
        return new ToolResponseMessage(responses);
    }

    private String argumentsJson(ToolCallRequest call) {
        // Vertex converts this field back into a protobuf Struct. Malformed raw
        // JSON cannot be replayed; its explicit parsing error is preserved in
        // the domain call and returned to Gemini as the linked tool error.
        if (!call.hasValidArgumentsJson()) {
            return "{}";
        }
        if (call.rawArguments() != null && !call.rawArguments().isBlank()) {
            return call.rawArguments();
        }
        try {
            return objectMapper.writeValueAsString(call.arguments());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize tool-call arguments", exception);
        }
    }

    private ToolCallback toToolCallback(com.finsight.finsight_ai.ai.chat.domain.ToolSpec spec) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(toProviderInputSchema(spec.jsonSchemaParameters()))
                .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                return "{\"status\":\"ERROR\",\"code\":\"INTERNAL_TOOL_EXECUTION_DISABLED\"}";
            }
        };
    }

    /**
     * Vertex AI's protobuf JSON parser in Spring AI 1.1.0-M1 expects enum-style
     * schema type names (OBJECT, STRING, ...). Domain tool schemas remain valid
     * standard JSON Schema; this conversion is confined to the Vertex adapter.
     */
    private String toProviderInputSchema(String standardJsonSchema) {
        try {
            JsonNode root = objectMapper.readTree(standardJsonSchema);
            uppercaseSchemaTypes(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Tool input schema is not valid JSON", exception);
        }
    }

    private static void uppercaseSchemaTypes(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            JsonNode type = objectNode.get("type");
            if (type != null && type.isTextual()) {
                objectNode.put("type", type.textValue().toUpperCase(java.util.Locale.ROOT));
            }
            objectNode.elements().forEachRemaining(VertexChatAdapter::uppercaseSchemaTypes);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(VertexChatAdapter::uppercaseSchemaTypes);
        }
    }

    private record ParsedArguments(Map<String, Object> arguments, String error) {
    }

}
