package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;

import com.hanbay.hotel.room.exception.RoomValidationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class RoomRecommendationConversationStore {
    private static final Duration TTL = Duration.ofHours(1);
    private static final Set<String> FIELDS = Arrays.stream(Criteria.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, Context> fallback = new HashMap<>();

    public record RoomReference(UUID id, String name) {}

    public record Context(
            long version,
            Criteria criteria,
            Instant expiresAt,
            List<RoomReference> rooms,
            UUID selectedRoomTypeId,
            List<String> pendingQuestions) {
        public Context {
            rooms = rooms == null ? List.of() : List.copyOf(rooms);
            pendingQuestions = pendingQuestions == null ? List.of() : List.copyOf(pendingQuestions);
        }

        public Context(long version, Criteria criteria, Instant expiresAt) {
            this(version, criteria, expiresAt, List.of(), null, List.of());
        }
    }

    public synchronized Context get(UUID user, UUID conversation) {
        String key = key(user, conversation);
        fallback.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(clock.instant()));
        try {
            String value = redis.opsForValue().get(key);
            if (value != null) {
                Context context = json.readValue(value, Context.class);
                if (context.expiresAt().isAfter(clock.instant())) return context;
            } else return empty();
        } catch (RuntimeException ignored) {
            /* Bounded, expiring fallback for a single application instance. */
        }
        return fallback.getOrDefault(key, empty());
    }

    public synchronized Context merge(UUID user, UUID conversation, Patch patch) {
        if (patch == null || patch.values() == null) throw new RoomValidationException("Thiếu dữ liệu nhu cầu tư vấn.");
        Context previous = get(user, conversation);
        ObjectNode merged = json.valueToTree(previous.criteria());
        ObjectNode changes = json.valueToTree(patch.values());
        for (String field : FIELDS) {
            if (!changes.hasNonNull(field)) continue;
            if (field.equals("budget") && merged.path(field).isObject()) {
                ObjectNode budget = (ObjectNode) merged.get(field);
                for (String key : List.of("amount", "period", "required"))
                    if (changes.get(field).hasNonNull(key))
                        budget.set(key, changes.get(field).get(key));
            } else merged.set(field, changes.get(field));
        }
        for (String field : list(patch.clearFields())) {
            if (!FIELDS.contains(field)) throw new RoomValidationException("Trường nhu cầu không hợp lệ: " + field);
            merged.putNull(field);
        }
        // Ages describe children at check-in. Never carry them to a different arrival date/count.
        if (!changes.hasNonNull("childAges")
                && (!Objects.equals(
                                merged.get("checkIn"),
                                json.valueToTree(previous.criteria().checkIn()))
                        || !Objects.equals(
                                merged.get("children"),
                                json.valueToTree(previous.criteria().children())))) merged.putNull("childAges");
        Criteria criteria = json.treeToValue(merged, Criteria.class);
        boolean changed = !criteria.equals(previous.criteria());
        Context next = new Context(
                previous.version() + 1,
                criteria,
                clock.instant().plus(TTL),
                changed ? List.of() : previous.rooms(),
                changed ? null : previous.selectedRoomTypeId(),
                List.of());
        save(key(user, conversation), next);
        return next;
    }

    public synchronized void remember(UUID user, UUID conversation, long version, Result result, UUID selected) {
        Context current = get(user, conversation);
        if (current.version() != version) return;
        List<RoomReference> rooms = result.status() == Status.RECOMMENDED
                ? result.recommendations().stream()
                        .map(r -> new RoomReference(r.room().id(), r.room().name()))
                        .toList()
                : List.of();
        UUID selection = selected == null ? current.selectedRoomTypeId() : selected;
        UUID validSelection = rooms.stream().anyMatch(r -> r.id().equals(selection)) ? selection : null;
        save(
                key(user, conversation),
                new Context(
                        version,
                        current.criteria(),
                        clock.instant().plus(TTL),
                        rooms,
                        validSelection,
                        result.questions()));
    }

    private void save(String key, Context next) {
        if (fallback.size() >= 1000)
            fallback.remove(fallback.keySet().iterator().next());
        fallback.put(key, next);
        try {
            redis.opsForValue().set(key, json.writeValueAsString(next), TTL);
        } catch (RuntimeException ignored) {
            /* The next request can still use the expiring fallback. */
        }
    }

    public synchronized void rememberSelection(UUID user, UUID conversation, long version, UUID selected) {
        Context current = get(user, conversation);
        if (current.version() != version || current.rooms().stream().noneMatch(r -> r.id().equals(selected))) return;
        save(
                key(user, conversation),
                new Context(
                        version, current.criteria(), clock.instant().plus(TTL), current.rooms(), selected, List.of()));
    }

    private Context empty() {
        return new Context(0, Criteria.builder().build(), clock.instant().plus(TTL));
    }

    private String key(UUID user, UUID conversation) {
        return "ai:conversation:" + user + ":" + conversation + ":room-requirements";
    }
}
