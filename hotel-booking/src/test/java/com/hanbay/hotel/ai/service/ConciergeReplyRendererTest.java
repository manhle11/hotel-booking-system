package com.hanbay.hotel.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class ConciergeReplyRendererTest {
    private final RoomRecommendationTool tool;
    private final String fallback;

    ConciergeReplyRendererTest() {
        var service = mock(com.hanbay.hotel.room.recommendation.RoomRecommendationService.class);
        var memory = mock(RoomRecommendationConversationStore.class);
        var user = UUID.randomUUID();
        var conversation = UUID.randomUUID();
        when(memory.merge(
                        org.mockito.ArgumentMatchers.eq(user),
                        org.mockito.ArgumentMatchers.eq(conversation),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RoomRecommendationConversationStore.Context(
                        1, RecommendationFixtures.CRITERIA, RecommendationFixtures.CLOCK.instant()));
        when(service.recommend(RecommendationFixtures.CRITERIA)).thenReturn(RecommendationFixtures.pair());
        tool = new RoomRecommendationTool(service, memory, user, conversation);
        tool.recommend(new com.hanbay.hotel.room.recommendation.RecommendationModels.Patch(
                RecommendationFixtures.CRITERIA, List.of()));
        fallback = tool.answer();
    }

    private String render(String text) {
        return ConciergeReplyRenderer.render(
                new ObjectMapper().writeValueAsString(Map.of("paragraphs", List.of(text))), tool);
    }

    @Test
    void rendersNaturalNarrativeWithBackendFactsRatherThanReplacingItWithTemplate() {
        assertThat(render("Mình thấy {{room.1.name}} đáng cân nhắc cho chuyến đi của bạn. {{room.1.price}}."))
                .isEqualTo(
                        "Mình thấy Superior Twin đáng cân nhắc cho chuyến đi của bạn. 3.400.000 VND tiền phòng cho 2 đêm.");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{{room.1.name}} giá 1.000.000 VND",
                "{{room.1.name}} giá một triệu",
                "{{room.1.name}} miễn phí bữa sáng",
                "{{room.1.name}} cách âm tuyệt đối",
                "Đã đặt {{room.1.name}}",
                "{{room.99.name}}",
                "Bạn có thể tiếp tục http://fake.test",
                "Phòng này rất tốt"
            })
    void fallsBackForInventedFactsOrInvalidReferences(String text) {
        assertThat(render(text)).isEqualTo(fallback);
    }

    @Test
    void rejectsMissingRequiredReferencesAndLiteralFactsInStructuredComposition() {
        var json = new ObjectMapper();
        String incomplete = json.writeValueAsString(
                new ConciergeReplyRenderer.Composition(List.of(new ConciergeReplyRenderer.Paragraph(
                        List.of(new ConciergeReplyRenderer.Segment(null, "room.1.name"))))));
        assertThat(ConciergeReplyRenderer.toTemplate(incomplete, tool)).isEmpty();
        var parts = List.of(
                new ConciergeReplyRenderer.Segment("Mình gợi ý ", null),
                new ConciergeReplyRenderer.Segment(null, "room.1.name"),
                new ConciergeReplyRenderer.Segment(": ", null),
                new ConciergeReplyRenderer.Segment(null, "room.1.price"),
                new ConciergeReplyRenderer.Segment("; còn ", null),
                new ConciergeReplyRenderer.Segment(null, "room.2.name"),
                new ConciergeReplyRenderer.Segment(": ", null),
                new ConciergeReplyRenderer.Segment(null, "room.2.price"));
        String valid = json.writeValueAsString(
                new ConciergeReplyRenderer.Composition(List.of(new ConciergeReplyRenderer.Paragraph(parts))));
        assertThat(ConciergeReplyRenderer.render(ConciergeReplyRenderer.toTemplate(valid, tool), tool))
                .contains("Superior Twin", "Deluxe Twin", "3.400.000", "5.000.000");
        var invented = new ArrayList<>(parts);
        invented.add(new ConciergeReplyRenderer.Segment(". Phòng có nhiều tiện nghi và rộng rãi.", null));
        String invalid = json.writeValueAsString(
                new ConciergeReplyRenderer.Composition(List.of(new ConciergeReplyRenderer.Paragraph(invented))));
        assertThat(ConciergeReplyRenderer.render(ConciergeReplyRenderer.toTemplate(invalid, tool), tool))
                .isEqualTo(fallback);
    }

    @Test
    void malformedJsonCannotLeakToUi() {
        assertThat(ConciergeReplyRenderer.render("provider-private-json", tool)).isEqualTo(fallback);
    }

    @Test
    void policyAnswersMayContainEvidenceNumbersWithoutRoomFactReferences() {
        assertThat(ConciergeReplyRenderer.render("{\"paragraphs\":[\"Theo chính sách, nhận phòng từ 14:00.\"]}", null))
                .isEqualTo("Theo chính sách, nhận phòng từ 14:00.");
    }
}
