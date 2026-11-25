package com.hanbay.hotel.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanbay.hotel.room.recommendation.RoomRecommendationService;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.ObjectMappers;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.test.util.ReflectionTestUtils;

/** Checks the actual provider request of pinned Spring AI, without an API key or network call. */
class RoomRecommendationOpenAiSchemaTest {
    @Test
    void providerSchemaIsStrictButUnspecifiedFactsRemainNullable() {
        var tool = new RoomRecommendationTool(
                mock(RoomRecommendationService.class),
                mock(RoomRecommendationConversationStore.class),
                UUID.randomUUID(),
                UUID.randomUUID());
        var model = OpenAiChatModel.builder()
                .openAiClient(mock(OpenAIClient.class))
                .openAiClientAsync(mock(OpenAIClientAsync.class))
                .options(OpenAiChatOptions.builder().model("gpt-4.1-mini").build())
                .build();
        var options = OpenAiChatOptions.builder()
                .model("gpt-4.1-mini")
                .strict(true)
                .parallelToolCalls(false)
                .responseFormat(ConciergeReplyRenderer.format())
                .toolCallbacks(Arrays.asList(ToolCallbacks.from(tool)))
                .build();
        ChatCompletionCreateParams request = ReflectionTestUtils.invokeMethod(
                model, "createRequest", new Prompt(List.of(new UserMessage("Tìm phòng")), options), false);
        assertThat(request).isNotNull();
        assertThat(request.parallelToolCalls()).contains(false);
        assertThat(request.responseFormat().orElseThrow().toString()).contains("paragraphs");
        var functions = request.tools().orElseThrow();
        assertThat(functions).hasSize(4);
        for (var providerTool : functions) {
            var function = providerTool.asFunction().function();
            assertThat(function.strict()).contains(true);
            JsonNode schema =
                    ObjectMappers.jsonMapper().valueToTree(function.parameters().orElseThrow());
            assertStrictObjects(schema);
            if (function.name().equals("recommend_rooms")) {
                var criteria = schema.path("properties")
                        .path("patch")
                        .path("properties")
                        .path("values");
                assertThat(criteria.path("properties").has("checkIn")).isTrue();
                assertThat(criteria.path("properties").path("children").toString())
                        .contains("null");
                assertThat(criteria.path("properties").path("requiredBalcony").toString())
                        .contains("null");
                assertThat(criteria.path("properties").path("budget").toString())
                        .contains("null");
            }
        }
    }

    @Test
    void compositionSchemaRestrictsFactReferencesToCurrentBackendEvidence() throws Exception {
        JsonNode schema = ObjectMappers.jsonMapper()
                .readTree(ConciergeReplyRenderer.compositionFormat(Set.of("room.1.name", "room.1.price"))
                        .getJsonSchema());
        assertStrictObjects(schema);
        String allowed = schema.path("properties")
                .path("paragraphs")
                .path("items")
                .path("properties")
                .path("segments")
                .path("items")
                .path("properties")
                .path("factKey")
                .path("enum")
                .toString();
        assertThat(allowed).contains("room.1.name", "room.1.price", "null").doesNotContain("room.2.name");
    }

    private void assertStrictObjects(JsonNode schema) {
        if (schema.has("properties")) {
            assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
            Set<String> required = new HashSet<>();
            schema.path("required").forEach(v -> required.add(v.asText()));
            Set<String> properties = new HashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(properties::add);
            assertThat(required).isEqualTo(properties);
        }
        if (schema.isContainerNode()) schema.forEach(this::assertStrictObjects);
    }
}
