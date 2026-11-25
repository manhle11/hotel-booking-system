package com.hanbay.hotel.room.recommendation;

import com.hanbay.hotel.room.dto.AmenityDto;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Explicit aliases for catalog labels; unknown labels match only the same normalized label. */
public final class RecommendationAmenities {
    private RecommendationAmenities() {}

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("wifi", "WIFI"),
            Map.entry("wi fi", "WIFI"),
            Map.entry("wi fi toc do cao", "WIFI"),
            Map.entry("internet", "WIFI"),
            Map.entry("tv", "TV"),
            Map.entry("tivi", "TV"),
            Map.entry("dieu hoa", "AIR_CONDITIONING"),
            Map.entry("may lanh", "AIR_CONDITIONING"),
            Map.entry("air conditioning", "AIR_CONDITIONING"),
            Map.entry("minibar", "MINIBAR"),
            Map.entry("ban lam viec", "WORK_DESK"),
            Map.entry("work desk", "WORK_DESK"),
            Map.entry("bon tam", "BATHTUB"),
            Map.entry("bathtub", "BATHTUB"),
            Map.entry("may pha ca phe", "COFFEE_MACHINE"),
            Map.entry("coffee machine", "COFFEE_MACHINE"),
            Map.entry("tu lanh mini", "MINI_FRIDGE"),
            Map.entry("mini fridge", "MINI_FRIDGE"));

    public static String code(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return ALIASES.getOrDefault(
                normalized, normalized.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }

    public static Set<String> codes(List<AmenityDto> amenities) {
        return amenities.stream().map(a -> code(a.name())).collect(Collectors.toSet());
    }
}
