package com.hanbay.hotel.room.recommendation;

import com.hanbay.hotel.room.domain.BedType;
import com.hanbay.hotel.room.domain.RoomViewType;
import com.hanbay.hotel.room.dto.AmenityDto;
import com.hanbay.hotel.room.dto.response.AvailableRoomTypeResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import org.springframework.ai.tool.annotation.ToolParam;

public final class RecommendationModels {
    private RecommendationModels() {}

    public enum Status {
        NEEDS_CLARIFICATION,
        RECOMMENDED,
        NO_MATCH,
        TEMPORARILY_UNAVAILABLE
    }

    public enum BudgetPeriod {
        TOTAL_STAY,
        PER_NIGHT,
        AVERAGE_PER_NIGHT
    }

    public enum BedMatch {
        AT_LEAST,
        EXACT
    }

    public record Budget(
            @ToolParam(required = false) Long amount,
            @ToolParam(required = false) BudgetPeriod period,
            @ToolParam(required = false) Boolean required) {}

    public record Bed(BedType bedType, Integer quantity) {}

    /** Null means unspecified. Empty lists/clearFields deliberately remove a previous requirement. */
    @Builder(toBuilder = true)
    public record Criteria(
            @ToolParam(required = false) LocalDate checkIn,
            @ToolParam(required = false) LocalDate checkOut,
            @ToolParam(required = false) Integer adults,
            @ToolParam(required = false) Integer children,
            @ToolParam(required = false) List<Integer> childAges,
            @ToolParam(required = false) Budget budget,
            @ToolParam(required = false) List<Bed> requiredBeds,
            @ToolParam(required = false) BedMatch bedMatch,
            @ToolParam(required = false) List<Bed> preferredBeds,
            @ToolParam(required = false) List<String> requiredAmenities,
            @ToolParam(required = false) List<String> preferredAmenities,
            @ToolParam(required = false) BigDecimal minAreaM2,
            @ToolParam(required = false) BigDecimal preferredAreaM2,
            @ToolParam(required = false) RoomViewType requiredView,
            @ToolParam(required = false) RoomViewType preferredView,
            @ToolParam(required = false) Boolean requiredBalcony,
            @ToolParam(required = false) Boolean preferredBalcony,
            @ToolParam(required = false) Boolean requiredHighFloor,
            @ToolParam(required = false) Boolean preferredHighFloor,
            @ToolParam(required = false) Boolean economy,
            @ToolParam(required = false) Boolean spacious,
            @ToolParam(required = false) List<String> preferredCategories,
            @ToolParam(required = false) List<String> unsupportedRequirements,
            @ToolParam(required = false) Integer roomCount) {}

    public record Patch(Criteria values, List<String> clearFields) {}

    public record ScorePart(String code, int weight, double value, String explanation) {}

    public record Scored(Double score, List<ScorePart> breakdown) {}

    public record RecommendedRoom(
            AvailableRoomTypeResponse room,
            List<AmenityDto> amenities,
            Double score,
            List<ScorePart> scoreBreakdown,
            List<String> reasons,
            List<String> unmetPreferences) {}

    public record Rejection(String code, int count, String message) {}

    public record Result(
            Status status,
            Criteria criteria,
            Instant evaluatedAt,
            String currency,
            String priceScope,
            String scoringVersion,
            List<RecommendedRoom> recommendations,
            List<String> questions,
            List<Rejection> rejections,
            List<String> suggestions,
            List<String> warnings) {}

    public static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }
}
