package com.hanbay.hotel.room.recommendation;

import static com.hanbay.hotel.room.recommendation.RecommendationModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService;
import com.hanbay.hotel.assignment.RoomAssignmentFeasibilityService.Feasibility;
import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.domain.BookingNight;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.common.response.PageResponse;
import com.hanbay.hotel.pricing.dto.result.RoomPricingResult;
import com.hanbay.hotel.room.domain.*;
import com.hanbay.hotel.room.dto.AmenityDto;
import com.hanbay.hotel.room.dto.response.AvailableRoomTypeResponse;
import com.hanbay.hotel.room.exception.RoomValidationException;
import com.hanbay.hotel.room.mapper.RoomTypeMapper;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.AvailableRoomTypeSearchService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class RoomRecommendationServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T08:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final AvailableRoomTypeSearchService search = mock(AvailableRoomTypeSearchService.class);
    private final RoomTypeRepository types = mock(RoomTypeRepository.class);
    private final RoomAssignmentFeasibilityService feasibility = mock(RoomAssignmentFeasibilityService.class);
    private final RoomTypeMapper mapper = Mappers.getMapper(RoomTypeMapper.class);
    private final RoomScorer scorer = new RoomScorer();
    private final RoomRequirementValidator validator =
            new RoomRequirementValidator(new HotelStayPolicy(clock, new HotelStayProperties()));
    private final RoomRecommendationService service =
            new RoomRecommendationService(validator, search, types, mapper, feasibility, scorer, clock);
    private final LocalDate in = LocalDate.of(2026, 11, 28), out = in.plusDays(2);
    private final Criteria base =
            Criteria.builder().checkIn(in).checkOut(out).adults(2).children(0).build();
    private RoomType superior, deluxe;

    @BeforeEach
    void fixture() {
        superior = type("SUPERIOR_TWIN", 30, 1700000, RoomViewType.CITY);
        deluxe = type("DELUXE_TWIN", 36, 2500000, RoomViewType.SEA);
    }

    private RoomType type(String code, int area, long price, RoomViewType view) {
        var t = RoomType.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(code)
                .maxGuests(3)
                .capacityAdults(2)
                .capacityChildren(1)
                .basePrice(price)
                .areaM2(BigDecimal.valueOf(area))
                .viewType(view)
                .build();
        t.replaceBeds(Map.of(BedType.SINGLE, 2));
        return t;
    }

    private AvailableRoomTypeResponse room(RoomType t, long... prices) {
        List<BookingNight> nights = new ArrayList<>();
        for (int i = 0; i < prices.length; i++)
            nights.add(BookingNight.builder()
                    .stayDate(in.plusDays(i))
                    .pricePerNight(prices[i])
                    .build());
        long total = Arrays.stream(prices).sum();
        return mapper.toAvailableResponse(t, 2, 1, new RoomPricingResult(nights, total, 0, total));
    }

    private void candidates(List<RoomType> catalog, List<AvailableRoomTypeResponse> rooms) {
        when(search.search(in, out, 2, 0, null, 0, 100))
                .thenReturn(new PageResponse<>(rooms, 0, 100, rooms.size(), 1, true, true));
        when(types.findRecommendationDetails(anyList(), eq(RoomTypeStatus.ACTIVE)))
                .thenReturn(catalog);
        when(feasibility.check(anyCollection(), eq(in), eq(out))).thenAnswer(call -> {
            Map<UUID, Feasibility> m = new HashMap<>();
            ((Collection<UUID>) call.getArgument(0)).forEach(id -> m.put(id, Feasibility.FEASIBLE));
            return m;
        });
    }

    @Test
    void ranksCatalogExampleWithExplainableScoresAndKeepsServerPrices() {
        var a = room(superior, 1700000, 1700000);
        var b = room(deluxe, 2500000, 2500000);
        candidates(List.of(superior, deluxe), List.of(a, b));
        var c = base.toBuilder()
                .requiredBeds(List.of(new Bed(BedType.SINGLE, 2)))
                .budget(new Budget(6000000L, BudgetPeriod.TOTAL_STAY, true))
                .economy(true)
                .preferredAreaM2(BigDecimal.valueOf(36))
                .preferredView(RoomViewType.SEA)
                .build();
        var result = service.recommend(c);
        assertThat(result.status()).isEqualTo(Status.RECOMMENDED);
        assertThat(result.recommendations())
                .extracting(r -> r.room().code())
                .containsExactly("DELUXE_TWIN", "SUPERIOR_TWIN");
        assertThat(result.recommendations()).extracting(RecommendedRoom::score).containsExactly(81.3, 79.2);
        assertThat(result.recommendations().get(0).room()).isSameAs(b);
        assertThat(result.recommendations().get(0).scoreBreakdown())
                .extracting(ScorePart::weight)
                .containsExactly(35, 15, 10);
        assertThat(result.evaluatedAt()).isEqualTo(clock.instant());
    }

    @Test
    void nightlyBudgetCannotBePassedByUsingAnAverage() {
        var r = room(deluxe, 1000000, 3000000);
        assertThat(service.failures(
                        base.toBuilder()
                                .budget(new Budget(2500000L, BudgetPeriod.PER_NIGHT, true))
                                .build(),
                        r,
                        List.of()))
                .contains("BUDGET");
        assertThat(service.failures(
                        base.toBuilder()
                                .budget(new Budget(2500000L, BudgetPeriod.AVERAGE_PER_NIGHT, true))
                                .build(),
                        r,
                        List.of()))
                .doesNotContain("BUDGET");
        assertThat(service.failures(
                        base.toBuilder()
                                .budget(new Budget(3999999L, BudgetPeriod.TOTAL_STAY, true))
                                .build(),
                        r,
                        List.of()))
                .contains("BUDGET");
        assertThat(service.failures(
                        base.toBuilder()
                                .budget(new Budget(4000000L, BudgetPeriod.TOTAL_STAY, true))
                                .build(),
                        r,
                        List.of()))
                .isEmpty();
    }

    @Test
    void unknownFeaturesAndBedsNeverSatisfyMandatoryConstraints() {
        var c = base.toBuilder()
                .requiredBeds(List.of(new Bed(BedType.KING, 1)))
                .requiredBalcony(false)
                .requiredAmenities(List.of("WIFI"))
                .build();
        assertThat(service.failures(c, room(deluxe, 1, 1), List.of())).containsExactly("BEDS", "AMENITIES", "FEATURES");
        var legacy = RoomType.builder()
                .id(UUID.randomUUID())
                .code("LEGACY")
                .name("Twin")
                .bedType("2 SINGLE")
                .maxGuests(3)
                .capacityAdults(2)
                .capacityChildren(1)
                .build();
        assertThat(service.failures(
                        base.toBuilder()
                                .requiredBeds(List.of(new Bed(BedType.SINGLE, 2)))
                                .build(),
                        room(legacy, 1, 1),
                        List.of()))
                .contains("BEDS");
    }

    @Test
    void exactBedMatchingRejectsExtraKindsWhileAtLeastPermitsThem() {
        deluxe.replaceBeds(Map.of(BedType.SINGLE, 2, BedType.QUEEN, 1));
        var c = base.toBuilder()
                .requiredBeds(List.of(new Bed(BedType.SINGLE, 2)))
                .build();
        assertThat(service.failures(c, room(deluxe, 1, 1), List.of())).isEmpty();
        assertThat(service.failures(c.toBuilder().bedMatch(BedMatch.EXACT).build(), room(deluxe, 1, 1), List.of()))
                .contains("BEDS");
    }

    @Test
    void amenitiesUseExplicitAliasesAndDoNotInferFreeBreakfast() {
        var wifi = new AmenityDto(UUID.randomUUID(), "Wi-Fi tốc độ cao", "wifi");
        assertThat(service.failures(
                        base.toBuilder().requiredAmenities(List.of("internet")).build(),
                        room(deluxe, 1, 1),
                        List.of(wifi)))
                .isEmpty();
        assertThat(service.failures(
                        base.toBuilder()
                                .requiredAmenities(List.of("bữa sáng miễn phí"))
                                .build(),
                        room(deluxe, 1, 1),
                        List.of(wifi)))
                .contains("AMENITIES");
    }

    @Test
    void filtersTotalCapacityEvenIfAdultAndChildLimitsIndividuallyFit() {
        var c = base.toBuilder().adults(2).children(2).childAges(List.of(4, 8)).build();
        assertThat(service.failures(c, room(deluxe, 1, 1), List.of())).contains("CAPACITY");
    }

    @Test
    void asksForMissingFactsBeforeQueryingInventory() {
        var result = service.recommend(Criteria.builder()
                .children(1)
                .budget(new Budget(4000000L, null, null))
                .build());
        assertThat(result.status()).isEqualTo(Status.NEEDS_CLARIFICATION);
        assertThat(result.questions()).hasSize(6);
        verifyNoInteractions(search, types, feasibility);
    }

    @Test
    void refusesInvalidAgesDatesAndDuplicateBeds() {
        assertThatThrownBy(() -> service.recommend(
                        base.toBuilder().children(1).childAges(List.of(12)).build()))
                .hasMessageContaining("tuổi");
        assertThatThrownBy(() -> service.recommend(base.toBuilder().checkOut(in).build()))
                .isInstanceOf(RoomValidationException.class);
        assertThatThrownBy(() -> service.recommend(base.toBuilder()
                        .requiredBeds(List.of(new Bed(BedType.SINGLE, 1), new Bed(BedType.SINGLE, 2)))
                        .build()))
                .isInstanceOf(RoomValidationException.class);
        assertThat(service.recommend(base.toBuilder().roomCount(2).build()).status())
                .isEqualTo(Status.NEEDS_CLARIFICATION);
        assertThat(service.recommend(base.toBuilder()
                                .unsupportedRequirements(List.of("giường phụ"))
                                .build())
                        .status())
                .isEqualTo(Status.NEEDS_CLARIFICATION);
    }

    @Test
    void excludesPositiveInventoryWhenContinuousAssignmentIsImpossible() {
        candidates(List.of(deluxe), List.of(room(deluxe, 1, 1)));
        when(feasibility.check(anyCollection(), eq(in), eq(out)))
                .thenReturn(Map.of(deluxe.getId(), Feasibility.INFEASIBLE));
        var result = service.recommend(base);
        assertThat(result.status()).isEqualTo(Status.NO_MATCH);
        assertThat(result.recommendations()).isEmpty();
        assertThat(result.rejections()).extracting(Rejection::code).contains("CONTINUOUS_STAY");
    }

    @Test
    void distinguishesUnverifiedInventoryFromSoldOut() {
        candidates(List.of(deluxe), List.of(room(deluxe, 1, 1)));
        when(feasibility.check(anyCollection(), eq(in), eq(out)))
                .thenReturn(Map.of(deluxe.getId(), Feasibility.UNKNOWN));
        var result = service.recommend(base);
        assertThat(result.status()).isEqualTo(Status.TEMPORARILY_UNAVAILABLE);
        assertThat(result.recommendations()).isEmpty();
    }

    @Test
    void readsAllPagesThenLimitsTopThreeRatherThanTruncatingBeforeScoring() {
        var extra = type("BEST", 60, 100000, RoomViewType.SEA);
        var fourth = type("OTHER", 30, 2000000, RoomViewType.CITY);
        candidates(List.of(superior, deluxe, fourth, extra), List.of());
        when(search.search(in, out, 2, 0, null, 0, 100))
                .thenReturn(new PageResponse<>(
                        List.of(
                                room(superior, 1700000, 1700000),
                                room(deluxe, 2500000, 2500000),
                                room(fourth, 2000000, 2000000)),
                        0,
                        100,
                        4,
                        2,
                        true,
                        false));
        when(search.search(in, out, 2, 0, null, 1, 100))
                .thenReturn(new PageResponse<>(List.of(room(extra, 100000, 100000)), 1, 100, 4, 2, false, true));
        var result = service.recommend(base.toBuilder().economy(true).build());
        assertThat(result.recommendations()).hasSize(3);
        assertThat(result.recommendations().get(0).room().code()).isEqualTo("BEST");
    }

    @Test
    void refreshesReferencedRoomEvenWhenItFallsOutsideCurrentTopThree() {
        var third = type("THIRD", 30, 10, RoomViewType.CITY);
        var fourth = type("FOURTH", 30, 20, RoomViewType.CITY);
        candidates(
                List.of(superior, deluxe, third, fourth),
                List.of(room(superior, 1, 1), room(third, 10, 10), room(fourth, 20, 20), room(deluxe, 30, 30)));
        assertThat(service.recommend(base).recommendations())
                .extracting(r -> r.room().id())
                .doesNotContain(deluxe.getId());
        assertThat(service.recommendForRooms(base, List.of(deluxe.getId())).recommendations())
                .extracting(r -> r.room().id())
                .containsExactly(deluxe.getId());
        assertThat(service.recommendForRooms(
                                base.toBuilder()
                                        .budget(new Budget(50L, BudgetPeriod.TOTAL_STAY, true))
                                        .build(),
                                List.of(deluxe.getId()))
                        .status())
                .isEqualTo(Status.NO_MATCH);
    }

    @Test
    void noPreferencesHasNoInventedScoreAndZeroPriceIsFinite() {
        var r = room(deluxe, 0, 0);
        assertThat(scorer.score(base, r, List.of(), 0, 36).score()).isNull();
        assertThat(scorer.score(
                                base.toBuilder()
                                        .economy(true)
                                        .budget(new Budget(0L, BudgetPeriod.TOTAL_STAY, false))
                                        .build(),
                                r,
                                List.of(),
                                0,
                                36)
                        .score())
                .isEqualTo(100);
    }
}
