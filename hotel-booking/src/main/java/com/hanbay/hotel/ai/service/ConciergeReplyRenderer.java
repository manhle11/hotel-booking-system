package com.hanbay.hotel.ai.service;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import tools.jackson.databind.ObjectMapper;

/** Natural language surrounding immutable fact references. Unverified numeric claims never reach UI. */
public final class ConciergeReplyRenderer {
    public record Reply(List<String> paragraphs) {}

    public record Composition(List<Paragraph> paragraphs) {}

    public record Paragraph(List<Segment> segments) {}

    public record Segment(String text, String factKey) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern REFERENCE = Pattern.compile("\\{\\{([a-zA-Z0-9_.]+)}}");
    private static final Pattern UNSUPPORTED = Pattern.compile(
            "(?iu)(miễn phí|cách âm|yên tĩnh|đã đặt|đã giữ|đã thanh toán|đảm bảo còn phòng|bao gồm bữa sáng|free breakfast|nhiều tiện nghi|rộng rãi|cao cấp|sang trọng|giá hợp lý|giá mềm|giá rẻ|tiết kiệm hơn|rộng hơn|phù hợp gia đình|thanh toán|hoàn tất|hoàn thành đặt)");
    private static final Pattern MONEY_WORDS = Pattern.compile("(?iu)(triệu|nghìn|ngàn|trăm|tỷ|vnd|đồng|\\$)");

    private ConciergeReplyRenderer() {}

    public static ResponseFormat format() {
        ResponseFormat format = new ResponseFormat();
        format.setType(ResponseFormat.Type.JSON_SCHEMA);
        format.setStrict(true);
        format.setJsonSchema(
                """
                {"type":"object","properties":{"paragraphs":{"type":"array","items":{"type":"string"}}},"required":["paragraphs"],"additionalProperties":false}
                """);
        return format;
    }

    public static ResponseFormat compositionFormat(Set<String> factKeys) {
        List<String> allowed = new ArrayList<>(factKeys.stream().sorted().toList());
        allowed.add(null);
        Map<String, Object> segment = Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                        "text", Map.of("type", List.of("string", "null")),
                        "factKey", Map.of("type", List.of("string", "null"), "enum", allowed)),
                "required",
                List.of("text", "factKey"),
                "additionalProperties",
                false);
        Map<String, Object> paragraph = Map.of(
                "type",
                "object",
                "properties",
                Map.of("segments", Map.of("type", "array", "items", segment)),
                "required",
                List.of("segments"),
                "additionalProperties",
                false);
        ResponseFormat format = new ResponseFormat();
        format.setType(ResponseFormat.Type.JSON_SCHEMA);
        format.setStrict(true);
        format.setJsonSchema(JSON.writeValueAsString(Map.of(
                "type",
                "object",
                "properties",
                Map.of("paragraphs", Map.of("type", "array", "items", paragraph)),
                "required",
                List.of("paragraphs"),
                "additionalProperties",
                false)));
        return format;
    }

    /** A dedicated composition call sees key descriptions, never room/price values to copy into prose. */
    public static String toTemplate(String raw, RoomRecommendationTool tool) {
        try {
            Composition composition = JSON.readValue(raw, Composition.class);
            if (composition.paragraphs() == null
                    || composition.paragraphs().isEmpty()
                    || composition.paragraphs().size() > 5) return "";
            Set<String> used = new HashSet<>();
            List<String> paragraphs = new ArrayList<>();
            for (Paragraph paragraph : composition.paragraphs()) {
                if (paragraph.segments() == null
                        || paragraph.segments().isEmpty()
                        || paragraph.segments().size() > 24) return "";
                StringBuilder text = new StringBuilder();
                for (Segment segment : paragraph.segments()) {
                    if ((segment.text() == null) == (segment.factKey() == null)) return "";
                    if (segment.factKey() != null) {
                        if (!tool.facts().containsKey(segment.factKey())) return "";
                        used.add(segment.factKey());
                        text.append("{{").append(segment.factKey()).append("}}");
                    } else text.append(segment.text());
                }
                paragraphs.add(text.toString());
            }
            if (!used.containsAll(tool.requiredReferences())) return "";
            return JSON.writeValueAsString(new Reply(paragraphs));
        } catch (RuntimeException exception) {
            return "";
        }
    }

    public static String render(String raw, RoomRecommendationTool tool) {
        try {
            Reply reply = JSON.readValue(raw, Reply.class);
            if (reply.paragraphs() == null
                    || reply.paragraphs().isEmpty()
                    || reply.paragraphs().size() > 5) return fallback(tool);
            Map<String, String> facts = tool == null ? Map.of() : tool.facts();
            List<String> output = new ArrayList<>();
            boolean anyReference = false;
            for (String paragraph : reply.paragraphs()) {
                if (paragraph == null || paragraph.isBlank() || paragraph.length() > 2500) return fallback(tool);
                var matcher = REFERENCE.matcher(paragraph);
                StringBuilder resolved = new StringBuilder();
                while (matcher.find()) {
                    String fact = facts.get(matcher.group(1));
                    if (fact == null) return fallback(tool);
                    anyReference = true;
                    matcher.appendReplacement(resolved, java.util.regex.Matcher.quoteReplacement(fact));
                }
                matcher.appendTail(resolved);
                if (tool != null && tool.touched()) {
                    String literal = REFERENCE.matcher(paragraph).replaceAll("");
                    if (literal.matches("(?s).*\\d.*")
                            || MONEY_WORDS.matcher(literal).find()
                            || UNSUPPORTED.matcher(literal).find()
                            || literal.contains("{{")
                            || literal.contains("http")
                            || literal.contains("@@UI_ACTION@@")) return fallback(tool);
                    // Factual room attributes must be inserted as references, not invented prose.
                    if (!literal.endsWith("?")
                            && Pattern.compile("(?iu)(wi-?fi|giường|m²|ban công|hướng biển|minibar|điều hòa|bồn tắm)")
                                    .matcher(literal)
                                    .find()) return fallback(tool);
                }
                output.add(
                        resolved.toString().trim().replaceAll("\\.\\s*\\.", ".").replaceAll("\\.\\s*,", ";"));
            }
            if (tool != null
                    && tool.result() != null
                    && tool.result().status()
                            == com.hanbay.hotel.room.recommendation.RecommendationModels.Status.RECOMMENDED
                    && !anyReference) return fallback(tool);
            return String.join("\n\n", output);
        } catch (RuntimeException exception) {
            return fallback(tool);
        }
    }

    private static String fallback(RoomRecommendationTool tool) {
        return tool == null ? "" : tool.answer();
    }
}
