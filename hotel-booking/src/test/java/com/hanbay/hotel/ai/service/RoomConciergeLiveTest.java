package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.ai.service.RecommendationFixtures.*;
import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.ai.dto.AiChatStreamEvent;
import com.hanbay.hotel.ai.rag.PolicyRetriever;
import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService;
import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService.Feasibility;
import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.mapper.RoomTypeMapper;
import com.hanbay.hotel.room.recommendation.*;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.AvailableRoomTypeSearchService;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mapstruct.factory.Mappers;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

/** Opt-in: real model and tool routing, synthetic catalog, no hotel database or booking writes. */
@EnabledIfSystemProperty(named = "ai.concierge.live", matches = "true")
class RoomConciergeLiveTest {
    @Test
    void vietnameseConversationClarifiesRemembersComparesAndRechecksBeforeCheckout() throws Exception {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            Path path = Files.exists(Path.of("../.env")) ? Path.of("../.env") : Path.of(".env");
            key = Files.readAllLines(path).stream()
                    .filter(s -> s.startsWith("OPENAI_API_KEY="))
                    .map(s -> s.substring(s.indexOf('=') + 1).trim().replaceAll("^['\"]|['\"]$", ""))
                    .findFirst()
                    .orElseThrow();
        }
        var model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .apiKey(key)
                        .model("gpt-4.1-mini")
                        .temperature(0.2)
                        .store(false)
                        .timeout(Duration.ofSeconds(45))
                        .maxRetries(0)
                        .build())
                .build();
        ObjectProvider<ChatModel> models = mock(ObjectProvider.class);
        when(models.getIfAvailable()).thenReturn(model);
        ObjectProvider<PolicyRetriever> policies = mock(ObjectProvider.class);
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
        var current = mock(CurrentUserProvider.class);
        UUID user = UUID.randomUUID(), conversation = UUID.randomUUID();
        when(current.currentUserId()).thenReturn(Optional.of(user));
        var mapper = new ObjectMapper();
        var history = new AiConversationStore(redis, mapper, current);
        var memory = new RoomRecommendationConversationStore(redis, mapper, CLOCK);
        var search = mock(AvailableRoomTypeSearchService.class);
        var types = mock(RoomTypeRepository.class);
        var feasibility = mock(RoomAssignmentFeasibilityService.class);
        var catalog = pair().recommendations().stream()
                .map(r -> {
                    var room = r.room();
                    var type = RoomType.builder()
                            .id(room.id())
                            .code(room.code())
                            .name(room.name())
                            .areaM2(room.areaM2())
                            .build();
                    type.replaceBeds(Map.of(BedType.SINGLE, 2));
                    return type;
                })
                .toList();
        when(search.search(any(), any(), anyInt(), anyInt(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(
                        pair().recommendations().stream()
                                .map(RecommendedRoom::room)
                                .toList(),
                        0,
                        100,
                        2,
                        1,
                        true,
                        true));
        when(types.findRecommendationDetails(anyList(), eq(RoomTypeStatus.ACTIVE)))
                .thenReturn(catalog);
        when(feasibility.check(anyCollection(), any(), any()))
                .thenReturn(Map.of(SUPERIOR, Feasibility.FEASIBLE, DELUXE, Feasibility.FEASIBLE));
        var engine = new RoomRecommendationService(
                new RoomRequirementValidator(new HotelStayPolicy(CLOCK, new HotelStayProperties())),
                search,
                types,
                Mappers.getMapper(RoomTypeMapper.class),
                feasibility,
                new RoomScorer(),
                CLOCK);
        var assistant = new HotelAiAssistantService(
                models,
                mock(HotelAiTools.class),
                mock(AiRequestLogger.class),
                history,
                new AiUiActionParser(mapper),
                new AiTimeContext(CLOCK),
                new AiDateNormalizer(CLOCK),
                policies,
                engine,
                memory);
        var first = send(assistant, conversation, "Gia đình mình muốn đi nghỉ, bạn tư vấn giúp nhé");
        assertThat(first).noneMatch(e -> e.recommendations() != null || e.action() != null);
        String firstAnswer = text(first);
        assertThat(firstAnswer.chars().filter(c -> c == '?').count()).isBetween(1L, 2L);
        var found = send(
                assistant,
                conversation,
                "Nhận 01/12/2026, trả 03/12/2026. Hai người lớn và một bé 5 tuổi, cần đúng hai giường đơn. Tổng tiền phòng không vượt 6 triệu, ưu tiên phòng rộng.");
        var state = memory.get(user, conversation);
        assertThat(state.criteria().childAges()).containsExactly(5);
        assertThat(state.criteria().requiredBeds()).containsExactly(new Bed(BedType.SINGLE, 2));
        assertThat(state.criteria().bedMatch()).isEqualTo(BedMatch.EXACT);
        assertThat(state.criteria().budget()).isEqualTo(new Budget(6000000L, BudgetPeriod.TOTAL_STAY, true));
        assertThat(found)
                .anyMatch(e -> e.recommendations() != null
                        && e.recommendations().recommendations().size() == 2);
        assertThat(found).noneMatch(e -> e.action() != null);
        assertThat(text(found))
                .contains("Deluxe Twin", "Superior Twin", "3.400.000", "5.000.000")
                .doesNotContain("%", "trọng số");
        var compared = send(assistant, conversation, "So sánh hai phòng đó giúp mình, phòng nào tiết kiệm hơn?");
        assertThat(text(compared)).contains("Superior Twin", "1.600.000");
        UUID second = memory.get(user, conversation).rooms().get(1).id();
        var detail = send(assistant, conversation, "Phòng thứ hai có giường và tiện nghi gì?");
        assertThat(memory.get(user, conversation).selectedRoomTypeId()).isEqualTo(second);
        assertThat(text(detail)).contains("2 giường đơn");
        assertThat(detail).noneMatch(e -> e.action() != null);
        var selected = send(assistant, conversation, "Mình chọn phòng này nhé");
        var action = selected.stream()
                .filter(e -> e.action() != null)
                .findFirst()
                .orElseThrow()
                .action();
        assertThat(text(selected)).contains("tiếp tục");
        assertThat(action.params().roomTypeId()).isEqualTo(second);
        assertThat(action.params().childAges()).containsExactly(5);
        var unchanged = memory.get(user, conversation).criteria();
        assertThat(unchanged.budget()).isEqualTo(state.criteria().budget());
        assertThat(unchanged.requiredBeds()).isEqualTo(state.criteria().requiredBeds());
    }

    private List<AiChatStreamEvent> send(HotelAiAssistantService service, UUID conversation, String message) {
        var events = service.stream(conversation, message).collectList().block(Duration.ofSeconds(90));
        assertThat(events).isNotNull();
        assertThat(events.get(events.size() - 1).type()).isEqualTo("done");
        System.out.println("Live concierge: " + text(events));
        return events;
    }

    private String text(List<AiChatStreamEvent> events) {
        return events.stream()
                .filter(e -> e.type().equals("delta"))
                .map(AiChatStreamEvent::content)
                .collect(java.util.stream.Collectors.joining());
    }
}
