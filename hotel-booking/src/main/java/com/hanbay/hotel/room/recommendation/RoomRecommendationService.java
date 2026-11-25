package com.hanbay.hotel.room.recommendation;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;

import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService;
import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService.Feasibility;
import com.hanbay.hotel.room.domain.RoomTypeStatus;
import com.hanbay.hotel.room.dto.AmenityDto;
import com.hanbay.hotel.room.dto.response.AvailableRoomTypeResponse;
import com.hanbay.hotel.room.mapper.RoomTypeMapper;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.AvailableRoomTypeSearchService;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomRecommendationService {
    private final RoomRequirementValidator validator;
    private final AvailableRoomTypeSearchService search;
    private final RoomTypeRepository types;
    private final RoomTypeMapper mapper;
    private final RoomAssignmentFeasibilityService feasibility;
    private final RoomScorer scorer;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result recommend(Criteria c) {
        return recommendForRooms(c, null);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result recommendForRooms(Criteria c, List<UUID> requestedIds) {
        List<String> questions = validator.questions(c);
        if (!questions.isEmpty())
            return result(Status.NEEDS_CLARIFICATION, c, List.of(), questions, List.of(), List.of(), List.of());
        List<AvailableRoomTypeResponse> candidates = new ArrayList<>();
        int page = 0;
        while (true) {
            var found = search.search(c.checkIn(), c.checkOut(), c.adults(), c.children(), null, page++, 100);
            candidates.addAll(found.content());
            if (found.last()) break;
        }
        if (requestedIds != null) candidates.removeIf(r -> !requestedIds.contains(r.id()));
        if (candidates.isEmpty())
            return result(
                    Status.NO_MATCH,
                    c,
                    List.of(),
                    List.of(),
                    List.of(new Rejection(
                            "AVAILABILITY_OR_CAPACITY",
                            0,
                            "Không có loại phòng còn trống đáp ứng số người lớn, trẻ em và tổng khách cho kỳ ở này.")),
                    List.of("Thử ngày lưu trú khác hoặc chia khách vào nhiều phòng tại trang đặt phòng."),
                    List.of());
        var amenities = types
                .findRecommendationDetails(
                        candidates.stream().map(AvailableRoomTypeResponse::id).toList(), RoomTypeStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(t -> t.getId(), t -> mapper.sortedAmenities(t.getAmenities())));
        Map<String, Integer> rejected = new LinkedHashMap<>();
        List<AvailableRoomTypeResponse> eligible = new ArrayList<>();
        for (var r : candidates) {
            List<String> failures = failures(c, r, amenities.getOrDefault(r.id(), List.of()));
            if (!amenities.containsKey(r.id())) failures.add("CATALOG");
            if (failures.isEmpty()) eligible.add(r);
            else failures.forEach(code -> rejected.merge(code, 1, Integer::sum));
        }
        var checks = feasibility.check(
                eligible.stream().map(AvailableRoomTypeResponse::id).toList(), c.checkIn(), c.checkOut());
        boolean unknown = eligible.stream()
                .anyMatch(r -> checks.getOrDefault(r.id(), Feasibility.UNKNOWN) == Feasibility.UNKNOWN);
        eligible.removeIf(r -> {
            if (checks.getOrDefault(r.id(), Feasibility.UNKNOWN) == Feasibility.FEASIBLE) return false;
            rejected.merge(
                    checks.get(r.id()) == Feasibility.INFEASIBLE ? "CONTINUOUS_STAY" : "UNVERIFIED", 1, Integer::sum);
            return true;
        });
        long cheapest = eligible.stream()
                .mapToLong(AvailableRoomTypeResponse::roomTotal)
                .min()
                .orElse(0);
        double largest = eligible.stream()
                .filter(r -> r.areaM2() != null)
                .mapToDouble(r -> r.areaM2().doubleValue())
                .max()
                .orElse(0);
        var rooms = eligible.stream()
                .map(r -> {
                    List<AmenityDto> provided = amenities.get(r.id());
                    Scored s = scorer.score(c, r, provided, cheapest, largest);
                    List<String> reasons = new ArrayList<>();
                    reasons.add("Đủ sức chứa cho " + c.adults() + " người lớn và " + c.children() + " trẻ em.");
                    if (!list(c.requiredBeds()).isEmpty()) reasons.add("Đáp ứng cấu hình giường bắt buộc.");
                    if (!list(c.requiredAmenities()).isEmpty()) reasons.add("Có đầy đủ tiện nghi bắt buộc.");
                    if (c.budget() != null && Boolean.TRUE.equals(c.budget().required()))
                        reasons.add("Giá tiền phòng đáp ứng ngân sách bắt buộc.");
                    if (r.viewType() != null)
                        reasons.add("Hướng nhìn: "
                                + switch (r.viewType()) {
                                    case SEA -> "biển";
                                    case CITY -> "thành phố";
                                    case COURTYARD -> "sân trong";
                                } + ".");
                    if (r.areaM2() != null)
                        reasons.add(
                                "Diện tích " + r.areaM2().stripTrailingZeros().toPlainString() + " m².");
                    List<String> unmet = new ArrayList<>(s.breakdown().stream()
                            .filter(p -> p.value() < 1 && !p.code().equals("PRICE"))
                            .map(p -> switch (p.code()) {
                                case "AREA" -> p.explanation()
                                        + " Có phương án khác đáp ứng tốt hơn ưu tiên diện tích.";
                                case "BEDS" -> "Cấu hình giường chưa đáp ứng đầy đủ sở thích.";
                                case "AMENITIES" -> "Một số tiện nghi ưu tiên chưa được khai báo cho phòng này.";
                                default -> "Một số ưu tiên về hướng nhìn, ban công, tầng hoặc nhóm phòng chưa đáp ứng.";
                            })
                            .toList());
                    if (c.budget() != null
                            && !Boolean.TRUE.equals(c.budget().required())
                            && RoomScorer.budgetPrice(c.budget(), r)
                                    > c.budget().amount()) unmet.add("Giá vượt mức ngân sách ưu tiên đã khai báo.");
                    return new RecommendedRoom(
                            r, provided, s.score(), s.breakdown(), List.copyOf(reasons), List.copyOf(unmet));
                })
                .sorted(Comparator.comparing((RecommendedRoom r) -> r.score() == null ? 0 : -r.score())
                        .thenComparingLong(r -> r.room().roomTotal())
                        .thenComparing(r -> r.room().code()))
                .limit(3)
                .toList();
        var rejections = rejected.entrySet().stream()
                .map(e -> new Rejection(e.getKey(), e.getValue(), rejectionMessage(e.getKey())))
                .toList();
        List<String> suggestions = new ArrayList<>();
        if (rooms.isEmpty()) {
            if (rejected.containsKey("BUDGET"))
                suggestions.add("Tăng ngân sách tiền phòng hoặc chọn kỳ ở ngắn hơn; giá từng đêm có thể khác nhau.");
            if (rejected.containsKey("BEDS"))
                suggestions.add("Cân nhắc cấu hình giường khác. Chỉ đổi điều kiện sau khi bạn lựa chọn.");
            if (rejected.containsKey("AMENITIES") || rejected.containsKey("FEATURES"))
                suggestions.add("Cân nhắc bỏ một tiện nghi hoặc đặc điểm bắt buộc chưa đáp ứng.");
            if (rejected.containsKey("CONTINUOUS_STAY"))
                suggestions.add("Thử ngày khác vì chưa xếp được một phòng liên tục cho toàn kỳ.");
        }
        List<String> warnings = new ArrayList<>(
                List.of(
                        "Giá chỉ gồm tiền phòng, chưa gồm dịch vụ mua thêm. Kết quả không giữ chỗ; giá và tồn được kiểm tra lại khi đặt."));
        if (unknown)
            warnings.add(
                    "Một số loại phòng chưa thể xác minh lịch trong thời gian cho phép; kết quả chỉ xếp hạng các loại đã xác minh.");
        return result(
                !rooms.isEmpty() ? Status.RECOMMENDED : unknown ? Status.TEMPORARILY_UNAVAILABLE : Status.NO_MATCH,
                c,
                rooms,
                List.of(),
                rejections,
                suggestions,
                warnings);
    }

    List<String> failures(Criteria c, AvailableRoomTypeResponse r, List<AmenityDto> amenities) {
        List<String> failures = new ArrayList<>();
        if (r.availableRooms() <= 0) failures.add("AVAILABILITY");
        if (c.adults() > r.adultCapacity()
                || c.children() > r.childCapacity()
                || c.adults() + c.children() > r.maxGuests()) failures.add("CAPACITY");
        if (!list(c.requiredBeds()).isEmpty()) {
            boolean matches = list(c.requiredBeds()).stream().allMatch(b -> r.beds().stream()
                    .anyMatch(x -> x.bedType() == b.bedType()
                            && (c.bedMatch() == BedMatch.EXACT
                                    ? x.quantity() == b.quantity()
                                    : x.quantity() >= b.quantity())));
            if (!matches
                    || (c.bedMatch() == BedMatch.EXACT
                            && r.beds().size() != c.requiredBeds().size())) failures.add("BEDS");
        }
        Set<String> provided = RecommendationAmenities.codes(amenities);
        if (!list(c.requiredAmenities()).stream()
                .map(RecommendationAmenities::code)
                .allMatch(provided::contains)) failures.add("AMENITIES");
        if ((c.minAreaM2() != null && (r.areaM2() == null || r.areaM2().compareTo(c.minAreaM2()) < 0))
                || (c.requiredView() != null && c.requiredView() != r.viewType())
                || (c.requiredBalcony() != null && !c.requiredBalcony().equals(r.hasBalcony()))
                || (c.requiredHighFloor() != null && !c.requiredHighFloor().equals(r.highFloor())))
            failures.add("FEATURES");
        if (c.budget() != null
                && Boolean.TRUE.equals(c.budget().required())
                && RoomScorer.budgetPrice(c.budget(), r) > c.budget().amount()) failures.add("BUDGET");
        return failures;
    }

    private String rejectionMessage(String code) {
        return switch (code) {
            case "BUDGET" -> "Vượt ngân sách bắt buộc.";
            case "BEDS" -> "Không đáp ứng cấu hình giường bắt buộc hoặc giường chưa được xác minh.";
            case "AMENITIES" -> "Thiếu tiện nghi bắt buộc trong dữ liệu khách sạn.";
            case "FEATURES" -> "Không đáp ứng hoặc chưa xác minh đặc điểm bắt buộc.";
            case "CONTINUOUS_STAY" -> "Không xếp được phòng liên tục cho toàn kỳ.";
            case "UNVERIFIED" -> "Chưa thể xác minh lịch phòng, không kết luận hết phòng.";
            default -> "Không đáp ứng trạng thái kinh doanh, tồn hoặc sức chứa.";
        };
    }

    private Result result(
            Status status,
            Criteria c,
            List<RecommendedRoom> rooms,
            List<String> questions,
            List<Rejection> rejected,
            List<String> suggestions,
            List<String> warnings) {
        return new Result(
                status,
                c,
                clock.instant(),
                "VND",
                "ROOM_ONLY",
                "v1",
                rooms,
                questions,
                rejected,
                suggestions,
                warnings);
    }
}
