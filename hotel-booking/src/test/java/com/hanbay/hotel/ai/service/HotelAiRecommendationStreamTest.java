package com.hanbay.hotel.ai.service;

import static com.hanbay.hotel.ai.service.RecommendationFixtures.*;
import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.ai.dto.AiChatStreamEvent;
import com.hanbay.hotel.ai.rag.PolicyRetriever;
import com.hanbay.hotel.room.recommendation.RoomRecommendationService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

class HotelAiRecommendationStreamTest {
    private final ChatModel model = mock(ChatModel.class);
    private final ObjectProvider<ChatModel> models = mock(ObjectProvider.class);
    private final ObjectProvider<PolicyRetriever> policies = mock(ObjectProvider.class);
    private final AiConversationStore conversations = mock(AiConversationStore.class);
    private final RoomRecommendationConversationStore memory = mock(RoomRecommendationConversationStore.class);
    private final RoomRecommendationService recommendations = mock(RoomRecommendationService.class);
    private final UUID user = UUID.randomUUID(), conversation = UUID.randomUUID();
    private HotelAiAssistantService assistant;

    @BeforeEach
    void setup() {
        when(models.getIfAvailable()).thenReturn(model);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(conversations.requireCurrentUserId()).thenReturn(user);
        when(conversations.get(user, conversation))
                .thenReturn(List.of(
                        new AiConversationStore.Message("user", "Tôi muốn đi nghỉ"),
                        new AiConversationStore.Message("assistant", "Bạn dự định ở ngày nào?")));
        when(memory.get(user, conversation))
                .thenReturn(new RoomRecommendationConversationStore.Context(1, CRITERIA, Instant.now()));
        when(memory.merge(eq(user), eq(conversation), any()))
                .thenReturn(new RoomRecommendationConversationStore.Context(2, CRITERIA, Instant.now()));
        assistant = new HotelAiAssistantService(
                models,
                mock(HotelAiTools.class),
                mock(AiRequestLogger.class),
                conversations,
                new AiUiActionParser(new ObjectMapper()),
                new AiTimeContext(CLOCK),
                new AiDateNormalizer(CLOCK),
                policies,
                recommendations,
                memory);
    }

    private Flux<ChatResponse> reply(String content) {
        return Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }

    private void callRecommendation(Prompt prompt) {
        var options = (ToolCallingChatOptions) prompt.getOptions();
        assertThat(((OpenAiChatOptions) options).getStrict()).isTrue();
        assertThat(((OpenAiChatOptions) options).getResponseFormat()).isNotNull();
        options.getToolCallbacks().stream()
                .filter(t -> t.getToolDefinition().name().equals("recommend_rooms"))
                .findFirst()
                .orElseThrow()
                .call("{\"patch\":{\"values\":{\"adults\":2},\"clearFields\":[]},\"maxOptions\":2}");
    }

    @Test
    void preservesKnownQuestionsAfterProviderFailureWithoutRawProviderErrorsOrEmptyCards() {
        when(recommendations.recommend(CRITERIA))
                .thenReturn(new Result(
                        Status.NEEDS_CLARIFICATION,
                        CRITERIA,
                        Instant.now(),
                        "VND",
                        "ROOM_ONLY",
                        "v1",
                        List.of(),
                        List.of("Bạn ưu tiên cấu hình giường nào?"),
                        List.of(),
                        List.of(),
                        List.of()));
        when(model.stream(any(Prompt.class)))
                .thenAnswer(c -> Flux.defer(() -> {
                    callRecommendation(c.getArgument(0));
                    return Flux.error(new IllegalStateException("private-provider-error"));
                }));
        var events = assistant.stream(conversation, "Tìm phòng").collectList().block();
        assertThat(events).extracting(AiChatStreamEvent::type).containsExactly("status", "status", "delta", "done");
        assertThat(events.get(2).content()).isEqualTo("Bạn ưu tiên cấu hình giường nào?");
        verify(conversations).append(user, conversation, "Tìm phòng", "Bạn ưu tiên cấu hình giường nào?");
    }

    @Test
    void sendsNativeRolesAndPreservesNaturalModelWordingWithVerifiedFactSubstitution() {
        when(recommendations.recommend(CRITERIA))
                .thenReturn(result(CRITERIA, pair().recommendations().get(0)));
        when(model.stream(any(Prompt.class)))
                .thenAnswer(c -> Flux.defer(() -> {
                    Prompt prompt = c.getArgument(0);
                    if (prompt.getSystemMessage().getText().contains("COMPOSE_VERIFIED")) {
                        return reply(
                                "{\"paragraphs\":[{\"segments\":[{\"text\":\"Với chuyến đi gia đình, mình nghiêng về \" ,\"factKey\":null},{\"text\":null,\"factKey\":\"room.1.name\"},{\"text\":\". \" ,\"factKey\":null},{\"text\":null,\"factKey\":\"room.1.price\"},{\"text\":\".\",\"factKey\":null}]},{\"segments\":[{\"text\":\"Bạn muốn mình đối chiếu thêm các lựa chọn không?\",\"factKey\":null}]}]}");
                    }
                    assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
                    assertThat(prompt.getInstructions().get(2)).isInstanceOf(AssistantMessage.class);
                    assertThat(prompt.getInstructions()
                                    .get(prompt.getInstructions().size() - 1)
                                    .getText())
                            .isEqualTo("Gia đình mình đi đầu tháng");
                    callRecommendation(prompt);
                    return reply(
                            "{\"paragraphs\":[\"Với chuyến đi gia đình, mình nghiêng về {{room.1.name}}. {{room.1.price}}.\",\"Bạn muốn mình đối chiếu thêm các lựa chọn không?\"]}");
                }));
        var events = assistant.stream(conversation, "Gia đình mình đi đầu tháng")
                .collectList()
                .block();
        assertThat(events)
                .extracting(AiChatStreamEvent::type)
                .containsExactly(
                        "status", "status", "status", "delta", "delta", "recommendations", "suggestions", "done");
        assertThat(events.get(3).content())
                .isEqualTo(
                        "Với chuyến đi gia đình, mình nghiêng về Superior Twin. 3.400.000 VND tiền phòng cho 2 đêm.");
        assertThat(events.get(6).suggestions()).contains("Cho tôi tìm hiểu phòng này");
    }

    @Test
    void unknownAvailabilityUsesBackendMessageWithoutAnotherModelClaim() {
        when(recommendations.recommend(CRITERIA)).thenReturn(new Result(Status.TEMPORARILY_UNAVAILABLE,CRITERIA,Instant.now(),"VND","ROOM_ONLY","v1",List.of(),List.of(),List.of(),List.of(),List.of()));
        when(model.stream(any(Prompt.class))).thenAnswer(c -> Flux.defer(() -> {
            callRecommendation(c.getArgument(0));return reply("{\"paragraphs\":[\"Đã hết phòng\"]}");
        }));
        var events=assistant.stream(conversation,"Tìm phòng").collectList().block();
        assertThat(events).extracting(AiChatStreamEvent::type).containsExactly("status","status","delta","done");
        assertThat(events.get(2).content()).contains("chưa xác minh").doesNotContain("hết phòng");
        verify(model,times(1)).stream(any(Prompt.class));
    }

    @Test
    void refusesModelGeneratedCheckoutWithoutBackendSelectionAndKeepsOtherActions() {
        String raw = "Mình đã tìm thấy thanh toán. @@UI_ACTION@@{\"type\":\"VIEW_PAYMENT\",\"params\":{\"paymentId\":\""
                + DELUXE + "\"}}";
        when(model.stream(any(Prompt.class)))
                .thenReturn(reply(new ObjectMapper().writeValueAsString(Map.of("paragraphs", List.of(raw)))));
        var events =
                assistant.stream(conversation, "Xem thanh toán").collectList().block();
        assertThat(events).extracting(AiChatStreamEvent::type).containsExactly("status", "delta", "action", "done");
        String malicious = "Tiếp tục nhé @@UI_ACTION@@{\"type\":\"CONTINUE_BOOKING\",\"params\":{\"roomTypeId\":\""
                + DELUXE + "\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\",\"adults\":2,\"children\":0}}";
        when(model.stream(any(Prompt.class)))
                .thenReturn(reply(new ObjectMapper().writeValueAsString(Map.of("paragraphs", List.of(malicious)))));
        assertThatThrownBy(() -> assistant.stream(conversation, "Tìm phòng")
                        .collectList()
                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no current backend evidence");
    }
}
