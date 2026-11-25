package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.room.recommendation.RoomRecommendationService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import tools.jackson.databind.ObjectMapper;

class RoomRecommendationToolTest {
    private final RoomRecommendationService service = mock(RoomRecommendationService.class);
    private final RoomRecommendationConversationStore store = mock(RoomRecommendationConversationStore.class);
    private final UUID user = UUID.randomUUID(), conversation = UUID.randomUUID();
    private final Criteria criteria = Criteria.builder().adults(2).build();
    private final Patch patch = new Patch(criteria, List.of());

    @Test
    void executesStructuredToolArgumentsAndExposesOnlyReadOnlyFunctions() {
        var tool = new RoomRecommendationTool(service, store, user, conversation);
        var result = new Result(
                Status.NEEDS_CLARIFICATION,
                criteria,
                Instant.now(),
                "VND",
                "ROOM_ONLY",
                "v1",
                List.of(),
                List.of("Bạn muốn nhận phòng ngày nào?"),
                List.of(),
                List.of(),
                List.of());
        when(store.merge(eq(user), eq(conversation), any()))
                .thenReturn(new RoomRecommendationConversationStore.Context(1, criteria, Instant.now()));
        when(service.recommend(criteria)).thenReturn(result);
        var callbacks = ToolCallbacks.from(tool);
        assertThat(callbacks)
                .extracting(t -> t.getToolDefinition().name())
                .containsExactlyInAnyOrder(
                        "recommend_rooms",
                        "inspect_recommended_room",
                        "compare_recommended_rooms",
                        "select_recommended_room");
        var callback = Arrays.stream(callbacks)
                .filter(t -> t.getToolDefinition().name().equals("recommend_rooms"))
                .findFirst()
                .orElseThrow();
        assertThat(callback.call("{\"patch\":{\"values\":{\"adults\":2},\"clearFields\":[]}}"))
                .contains("NEEDS_CLARIFICATION");
        assertThat(tool.answer()).isEqualTo("Bạn dự định nhận và trả phòng ngày nào?");
        verify(store).merge(eq(user), eq(conversation), any(Patch.class));
    }

    @Test
    void incompleteCriteriaAreOptionalInGeneratedSchemaSoTheModelCanAskForMissingFacts() throws Exception {
        var schema = new ObjectMapper().readTree(JsonSchemaGenerator.generateForType(Criteria.class));
        assertThat(schema.path("required").isMissingNode()
                        || schema.path("required").isEmpty())
                .isTrue();
        assertThat(schema.path("properties").has("checkIn")).isTrue();
        assertThat(schema.path("properties").has("budget")).isTrue();
    }

    @Test
    void limitsRepeatedQueriesInASingleModelTurn() {
        var tool = new RoomRecommendationTool(service, store, user, conversation);
        when(store.merge(user, conversation, patch))
                .thenReturn(new RoomRecommendationConversationStore.Context(1, criteria, Instant.now()));
        when(service.recommend(criteria))
                .thenReturn(new Result(
                        Status.NO_MATCH,
                        criteria,
                        Instant.now(),
                        "VND",
                        "ROOM_ONLY",
                        "v1",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        for (int i = 0; i < 6; i++) tool.recommend(patch);
        verify(service, times(4)).recommend(criteria);
    }

    @Test
    void invalidatesEarlierOutcomeWhenANewerRequirementQueryFails() {
        var tool = new RoomRecommendationTool(service, store, user, conversation);
        when(store.merge(user, conversation, patch))
                .thenReturn(new RoomRecommendationConversationStore.Context(1, criteria, Instant.now()));
        when(service.recommend(criteria))
                .thenReturn(new Result(
                        Status.NO_MATCH,
                        criteria,
                        Instant.now(),
                        "VND",
                        "ROOM_ONLY",
                        "v1",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()))
                .thenThrow(new IllegalStateException("Database unavailable"));
        tool.recommend(patch);
        assertThat(tool.result()).isNotNull();
        assertThatThrownBy(() -> tool.recommend(patch)).isInstanceOf(IllegalStateException.class);
        assertThat(tool.result()).isNull();
        assertThat(tool.answer()).isEmpty();
    }
}
