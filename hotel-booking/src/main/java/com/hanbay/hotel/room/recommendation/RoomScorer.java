package com.hanbay.hotel.room.recommendation;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;

import com.hanbay.hotel.room.dto.AmenityDto;
import com.hanbay.hotel.room.dto.response.AvailableRoomTypeResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** v1: 35 price, 20 beds, 20 amenities, 15 area, 10 features; normalize only active preferences. */
@Component
public class RoomScorer {
    public Scored score(
            Criteria c, AvailableRoomTypeResponse r, List<AmenityDto> amenities, long cheapest, double largest) {
        List<ScorePart> parts = new ArrayList<>();
        boolean softBudget =
                c.budget() != null && Boolean.FALSE.equals(c.budget().required());
        if (Boolean.TRUE.equals(c.economy()) || softBudget) {
            double fit = 1;
            if (Boolean.TRUE.equals(c.economy())) fit *= r.roomTotal() == 0 ? 1 : (double) cheapest / r.roomTotal();
            if (softBudget) {
                double price = budgetPrice(c.budget(), r);
                fit *= price == 0 ? 1 : Math.min(1, c.budget().amount() / price);
            }
            parts.add(new ScorePart("PRICE", 35, fit, "Ưu tiên tiết kiệm/ngân sách tiền phòng."));
        }
        if (!list(c.preferredBeds()).isEmpty()) {
            double fit = list(c.preferredBeds()).stream()
                    .mapToDouble(b -> Math.min(
                            1,
                            (double) r.beds().stream()
                                            .filter(x -> x.bedType() == b.bedType())
                                            .mapToInt(x -> x.quantity())
                                            .sum()
                                    / b.quantity()))
                    .average()
                    .orElse(0);
            parts.add(new ScorePart("BEDS", 20, fit, "Mức đáp ứng cấu hình giường ưu tiên."));
        }
        Set<String> provided = RecommendationAmenities.codes(amenities);
        Set<String> preferred = list(c.preferredAmenities()).stream()
                .map(RecommendationAmenities::code)
                .collect(java.util.stream.Collectors.toSet());
        if (!preferred.isEmpty())
            parts.add(new ScorePart(
                    "AMENITIES",
                    20,
                    (double) preferred.stream().filter(provided::contains).count() / preferred.size(),
                    "Mức đáp ứng tiện nghi ưu tiên."));
        if (c.preferredAreaM2() != null || Boolean.TRUE.equals(c.spacious())) {
            double target =
                    c.preferredAreaM2() == null ? largest : c.preferredAreaM2().doubleValue();
            double fit = r.areaM2() == null || target <= 0
                    ? 0
                    : Math.min(1, r.areaM2().doubleValue() / target);
            parts.add(new ScorePart(
                    "AREA",
                    15,
                    fit,
                    r.areaM2() == null
                            ? "Chưa xác minh diện tích."
                            : "Diện tích " + r.areaM2().stripTrailingZeros().toPlainString() + " m²."));
        }
        List<Boolean> features = new ArrayList<>();
        if (c.preferredView() != null) features.add(c.preferredView() == r.viewType());
        if (c.preferredBalcony() != null) features.add(c.preferredBalcony().equals(r.hasBalcony()));
        if (c.preferredHighFloor() != null) features.add(c.preferredHighFloor().equals(r.highFloor()));
        if (!list(c.preferredCategories()).isEmpty())
            features.add(r.category() != null
                    && list(c.preferredCategories()).stream()
                            .anyMatch(code -> code.equalsIgnoreCase(r.category().code())));
        if (!features.isEmpty())
            parts.add(new ScorePart(
                    "FEATURES",
                    10,
                    (double) features.stream().filter(Boolean::booleanValue).count() / features.size(),
                    "Mức đáp ứng hướng nhìn, ban công, tầng và nhóm phòng ưu tiên."));
        if (parts.isEmpty()) return new Scored(null, List.of());
        double score = 100
                * parts.stream().mapToDouble(p -> p.weight() * p.value()).sum()
                / parts.stream().mapToInt(ScorePart::weight).sum();
        return new Scored(Math.round(score * 10) / 10.0, List.copyOf(parts));
    }

    public static double budgetPrice(Budget b, AvailableRoomTypeResponse r) {
        return switch (b.period()) {
            case TOTAL_STAY -> r.roomTotal();
            case PER_NIGHT -> r.nightlyPrices().stream()
                    .mapToLong(n -> n.price())
                    .max()
                    .orElse(Long.MAX_VALUE);
            case AVERAGE_PER_NIGHT -> (double) r.roomTotal() / r.totalNights();
        };
    }
}
