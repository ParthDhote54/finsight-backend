package com.finsight.finsight_ai.ai.chat.adapters.out.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.domain.*;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VertexChatAdapterTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final VertexChatAdapter adapter = new VertexChatAdapter(chatModel, objectMapper);

    @Test
    void exposesProviderCompatibleToolDefinitionsAndDisablesInternalExecution() throws Exception {
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response(new AssistantMessage("done")));
        ToolSpec spec = new ToolSpec("sample_tool", "Sample description", """
                {"type":"object","properties":{"month":{"type":"string"}},"required":["month"]}
                """);

        adapter.generate(new ChatModelInput("system", List.of(), "question", List.of(spec)));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        assertThat(captor.getValue().getOptions()).isInstanceOf(VertexAiGeminiChatOptions.class);
        VertexAiGeminiChatOptions options = (VertexAiGeminiChatOptions) captor.getValue().getOptions();
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getCandidateCount()).isEqualTo(1);
        assertThat(options.getToolCallbacks()).singleElement().satisfies(callback -> {
            assertThat(callback.getToolDefinition().name()).isEqualTo("sample_tool");
            assertThat(callback.getToolDefinition().description()).isEqualTo("Sample description");
            Schema.Builder schema = Schema.newBuilder();
            try {
                JsonFormat.parser().ignoringUnknownFields()
                        .merge(callback.getToolDefinition().inputSchema(), schema);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            assertThat(schema.getType()).isEqualTo(Type.OBJECT);
            assertThat(schema.getPropertiesOrThrow("month").getType()).isEqualTo(Type.STRING);
        });
    }

    @Test
    void mapsStructuredCallsAndPreservesMalformedArgumentState() {
        AssistantMessage valid = new AssistantMessage("", Map.of(), List.of(
                new AssistantMessage.ToolCall("", "function", "top_merchants", "{\"limit\":5}")));
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response(valid));

        ChatModelOutput output = adapter.generate(input(List.of()));

        assertThat(output.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.callId()).isNotBlank();
            assertThat(call.toolName()).isEqualTo("top_merchants");
            assertThat(call.rawArguments()).isEqualTo("{\"limit\":5}");
            assertThat(call.arguments()).containsEntry("limit", 5);
            assertThat(call.hasValidArgumentsJson()).isTrue();
        });

        AssistantMessage malformed = new AssistantMessage("", Map.of(), List.of(
                new AssistantMessage.ToolCall("call-2", "function", "top_merchants", "{broken")));
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response(malformed));

        ToolCallRequest malformedCall = adapter.generate(input(List.of())).toolCalls().get(0);
        assertThat(malformedCall.rawArguments()).isEqualTo("{broken");
        assertThat(malformedCall.hasValidArgumentsJson()).isFalse();
    }

    @Test
    void continuationUsesNativeAssistantAndToolResponseMessages() {
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(response(new AssistantMessage("done")));
        ToolCallRequest call = new ToolCallRequest(
                "call-1", "top_merchants", Map.of("limit", 5), "{\"limit\":5}", null);
        List<ChatTurn> history = List.of(
                new ChatTurn(Role.USER, "Show my top merchants"),
                ChatTurn.assistant("", List.of(call)),
                ChatTurn.toolResults(List.of(new ToolCallResult(
                        "call-1", "top_merchants", "{\"merchants\":[]}")))
        );

        adapter.generate(input(history));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        var messages = captor.getValue().getInstructions();
        assertThat(messages).anySatisfy(message -> assertThat(message).isInstanceOf(UserMessage.class));
        assertThat(messages).anySatisfy(message -> {
            assertThat(message).isInstanceOf(AssistantMessage.class);
            AssistantMessage assistant = (AssistantMessage) message;
            assertThat(assistant.getToolCalls()).singleElement().satisfies(toolCall -> {
                assertThat(toolCall.id()).isEqualTo("call-1");
                assertThat(toolCall.name()).isEqualTo("top_merchants");
                assertThat(toolCall.arguments()).isEqualTo("{\"limit\":5}");
            });
        });
        assertThat(messages).anySatisfy(message -> {
            assertThat(message).isInstanceOf(ToolResponseMessage.class);
            ToolResponseMessage toolResponse = (ToolResponseMessage) message;
            assertThat(toolResponse.getResponses()).singleElement().satisfies(result -> {
                assertThat(result.id()).isEqualTo("call-1");
                assertThat(result.name()).isEqualTo("top_merchants");
            });
        });
        assertThat(messages).filteredOn(UserMessage.class::isInstance)
                .extracting(Object::toString)
                .noneMatch(text -> text.contains("Tool executed:"));
    }

    @Test
    void nullProviderResponseIsAnExplicitFailure() {
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(null);

        assertThatThrownBy(() -> adapter.generate(input(List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no generation");
    }

    private ChatModelInput input(List<ChatTurn> history) {
        return new ChatModelInput("system", history, history.isEmpty() ? "question" : null, List.of());
    }

    private static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }
}
