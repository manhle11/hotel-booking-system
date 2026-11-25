package com.hanbay.hotel.ai.dto;

import java.util.List;

public record AiChatStreamEvent(
        String type,
        String content,
        AiUiAction action,
        List<AiPolicySource> sources,
        com.hanbay.hotel.room.recommendation.RecommendationModels.Result recommendations,
        List<String> suggestions) {

    public AiChatStreamEvent(
            String type,
            String content,
            AiUiAction action,
            List<AiPolicySource> sources,
            com.hanbay.hotel.room.recommendation.RecommendationModels.Result recommendations) {
        this(type, content, action, sources, recommendations, List.of());
    }

    public AiChatStreamEvent(String type, String content, AiUiAction action, List<AiPolicySource> sources) {
        this(type, content, action, sources, null);
    }

    public static AiChatStreamEvent recommendations(
            com.hanbay.hotel.room.recommendation.RecommendationModels.Result result) {
        return new AiChatStreamEvent("recommendations", "", null, List.of(), result);
    }

    public static AiChatStreamEvent delta(String content) {
        return new AiChatStreamEvent("delta", content, null, List.of());
    }

    public static AiChatStreamEvent status(String content) {
        return new AiChatStreamEvent("status", content, null, List.of());
    }

    public static AiChatStreamEvent suggestions(List<String> suggestions) {
        return new AiChatStreamEvent("suggestions", "", null, List.of(), null, List.copyOf(suggestions));
    }

    public static AiChatStreamEvent action(AiUiAction action) {
        return new AiChatStreamEvent("action", "", action, List.of());
    }

    public static AiChatStreamEvent sources(List<AiPolicySource> sources) {
        return new AiChatStreamEvent("sources", "", null, List.copyOf(sources));
    }

    public static AiChatStreamEvent done() {
        return new AiChatStreamEvent("done", "", null, List.of());
    }

    public static AiChatStreamEvent error() {
        return new AiChatStreamEvent("error", "Trợ lý AI chưa thể trả lời. Vui lòng thử lại.", null, List.of());
    }
}
