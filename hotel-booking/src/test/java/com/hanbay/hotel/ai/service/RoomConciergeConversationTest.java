package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.ai.service.RecommendationFixtures.*;
import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.room.recommendation.RoomRecommendationService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class RoomConciergeConversationTest {
    private final UUID user = UUID.randomUUID(), conversation = UUID.randomUUID();
    private final RoomRecommendationService service = mock(RoomRecommendationService.class);
    private RoomRecommendationConversationStore memory;

    @BeforeEach
    void setup() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> data = new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(c -> data.get(c.getArgument(0)));
        doAnswer(c -> {
                    data.put(c.getArgument(0), c.getArgument(1));
                    return null;
                })
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        memory = new RoomRecommendationConversationStore(redis, new ObjectMapper(), CLOCK);
        when(service.recommend(any())).thenReturn(pair());
    }

    private RoomRecommendationTool turn() {
        return new RoomRecommendationTool(service, memory, user, conversation);
    }

    private void search() {
        turn().recommend(new Patch(CRITERIA, List.of()));
    }

    @Test
    void remembersSecondRoomAcrossInspectionAndSelectionDespiteChangedRanking() {
        search();
        var deluxe = pair().recommendations().get(1);
        when(service.recommendForRooms(CRITERIA, List.of(DELUXE))).thenReturn(result(CRITERIA, deluxe));
        var detail = turn();
        detail.inspect(new RoomRecommendationTool.Selection(2, null));
        assertThat(detail.facts().get("room.1.name")).isEqualTo("Deluxe Twin");
        assertThat(detail.cards()).isNull();
        assertThat(detail.action()).isNull();
        assertThat(memory.get(user, conversation).rooms())
                .extracting(RoomRecommendationConversationStore.RoomReference::id)
                .containsExactly(SUPERIOR, DELUXE);
        assertThat(memory.get(user, conversation).selectedRoomTypeId()).isEqualTo(DELUXE);
        var choose = turn();
        choose.select(new RoomRecommendationTool.Selection(null, null));
        assertThat(choose.action().params().roomTypeId()).isEqualTo(DELUXE);
        assertThat(choose.action().params().childAges()).containsExactly(5);
        assertThat(choose.action().params().checkIn()).isEqualTo(CRITERIA.checkIn());
        verify(service, times(2)).recommendForRooms(CRITERIA, List.of(DELUXE));
        verify(service, times(1)).recommend(any());
    }

    @Test
    void asksWhenReferenceIsAmbiguousRatherThanSelectingFirstRoom() {
        search();
        var tool = turn();
        tool.select(new RoomRecommendationTool.Selection(null, null));
        assertThat(tool.action()).isNull();
        assertThat(tool.validationQuestion()).contains("phòng nào");
        verify(service, never()).recommendForRooms(any(), any());
    }

    @Test
    void comparisonUsesJavaDifferencesAndPreservesDisplayedOrder() {
        search();
        var original = pair();
        when(service.recommendForRooms(CRITERIA, List.of(SUPERIOR, DELUXE)))
                .thenReturn(result(
                        CRITERIA,
                        original.recommendations().get(1),
                        original.recommendations().get(0)));
        var tool = turn();
        tool.compare(null);
        assertThat(tool.cards().recommendations())
                .extracting(r -> r.room().id())
                .containsExactly(SUPERIOR, DELUXE);
        assertThat(tool.facts().get("comparison.price"))
                .isEqualTo("Superior Twin tiết kiệm hơn 1.600.000 VND cho cả kỳ");
        assertThat(tool.facts().get("comparison.area")).isEqualTo("Deluxe Twin rộng hơn 6 m²");
        assertThat(tool.action()).isNull();
    }

    @Test
    void soldOutAfterRecommendationNeverProvidesCheckout() {
        search();
        when(service.recommendForRooms(CRITERIA, List.of(DELUXE)))
                .thenReturn(new Result(
                        Status.NO_MATCH,
                        CRITERIA,
                        CLOCK.instant(),
                        "VND",
                        "ROOM_ONLY",
                        "v1",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        var tool = turn();
        tool.select(new RoomRecommendationTool.Selection(2, null));
        assertThat(tool.action()).isNull();
        assertThat(tool.cards()).isNull();
        assertThat(tool.answer()).contains("không còn");
        assertThat(memory.get(user, conversation).rooms()).isEmpty();
    }

    @Test
    void changingCriteriaInvalidatesReferencesAndStaleCompletionCannotOverwriteThem() {
        search();
        long previous = memory.get(user, conversation).version();
        var updated = memory.merge(
                user,
                conversation,
                new Patch(
                        Criteria.builder()
                                .checkIn(CRITERIA.checkIn().plusDays(1))
                                .build(),
                        List.of()));
        assertThat(updated.rooms()).isEmpty();
        assertThat(updated.selectedRoomTypeId()).isNull();
        assertThat(updated.criteria().childAges()).isNull();
        memory.remember(user, conversation, previous, pair(), DELUXE);
        assertThat(memory.get(user, conversation).rooms()).isEmpty();
    }
}
