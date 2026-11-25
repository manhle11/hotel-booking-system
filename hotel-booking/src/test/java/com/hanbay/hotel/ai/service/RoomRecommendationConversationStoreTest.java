package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class RoomRecommendationConversationStoreTest {
    private final UUID user = UUID.randomUUID(), conversation = UUID.randomUUID();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Clock clock = mock(Clock.class);
    private final Map<String, String> data = new HashMap<>();
    private RoomRecommendationConversationStore store;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T08:00:00Z"));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(c -> data.get(c.getArgument(0)));
        doAnswer(c -> {
                    data.put(c.getArgument(0), c.getArgument(1));
                    return null;
                })
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        store = new RoomRecommendationConversationStore(redis, new ObjectMapper(), clock);
    }

    @Test
    void roundTripsRedisAndPatchesWithoutLosingMandatoryRequirements() {
        var c = Criteria.builder()
                .checkIn(LocalDate.of(2026, 12, 1))
                .children(1)
                .childAges(List.of(5))
                .requiredAmenities(List.of("WIFI"))
                .requiredBalcony(false)
                .build();
        store.merge(user, conversation, new Patch(c, List.of()));
        var updated = store.merge(
                user, conversation, new Patch(Criteria.builder().economy(true).build(), List.of()));
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.criteria().requiredBalcony()).isFalse();
        assertThat(updated.criteria().requiredAmenities()).containsExactly("WIFI");
        assertThat(updated.criteria().childAges()).containsExactly(5);
        assertThat(store.get(UUID.randomUUID(), conversation).version()).isZero();
        assertThat(store.get(user, UUID.randomUUID()).version()).isZero();
    }

    @Test
    void clearsDeliberatelyAndInvalidatesAgesWhenArrivalChanges() {
        store.merge(
                user,
                conversation,
                new Patch(
                        Criteria.builder()
                                .checkIn(LocalDate.of(2026, 12, 1))
                                .children(1)
                                .childAges(List.of(5))
                                .requiredAmenities(List.of("WIFI"))
                                .build(),
                        List.of()));
        var updated = store.merge(
                user,
                conversation,
                new Patch(
                        Criteria.builder()
                                .checkIn(LocalDate.of(2026, 12, 2))
                                .requiredAmenities(List.of())
                                .build(),
                        List.of("budget")));
        assertThat(updated.criteria().requiredAmenities()).isEmpty();
        assertThat(updated.criteria().childAges()).isNull();
        assertThat(updated.criteria().budget()).isNull();
        assertThatThrownBy(() -> store.merge(
                        user, conversation, new Patch(Criteria.builder().build(), List.of("userId"))))
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    void fallbackExpiresAndDoesNotLeakAcrossUsers() {
        when(values.get(anyString())).thenThrow(new IllegalStateException("Redis unavailable"));
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(values)
                .set(anyString(), anyString(), any(Duration.class));
        store.merge(user, conversation, new Patch(Criteria.builder().adults(2).build(), List.of()));
        assertThat(store.get(user, conversation).criteria().adults()).isEqualTo(2);
        assertThat(store.get(UUID.randomUUID(), conversation).criteria().adults())
                .isNull();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-25T09:00:00Z"));
        assertThat(store.get(user, conversation).version()).isZero();
    }

    @Test
    void changingBudgetAmountPreservesItsScopeAndMandatoryFlag() {
        store.merge(
                user,
                conversation,
                new Patch(
                        Criteria.builder()
                                .budget(new Budget(3000000L, BudgetPeriod.TOTAL_STAY, true))
                                .build(),
                        List.of()));
        var updated = store.merge(
                user,
                conversation,
                new Patch(
                        Criteria.builder()
                                .budget(new Budget(4000000L, null, null))
                                .build(),
                        List.of()));
        assertThat(updated.criteria().budget()).isEqualTo(new Budget(4000000L, BudgetPeriod.TOTAL_STAY, true));
    }
}
