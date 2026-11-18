package com.hanbay.hotel.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.addon.domain.AddOnService;
import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.domain.*;
import com.hanbay.hotel.booking.domain.addon.BookingAddOn;
import com.hanbay.hotel.booking.dto.request.BookingChangeCommand;
import com.hanbay.hotel.booking.exception.StayModificationException;
import com.hanbay.hotel.booking.policy.*;
import com.hanbay.hotel.booking.repository.*;
import com.hanbay.hotel.common.security.CurrentUserProvider;
import com.hanbay.hotel.payment.domain.Payment;
import com.hanbay.hotel.pricing.dto.result.RoomPricingResult;
import com.hanbay.hotel.pricing.service.PricingService;
import com.hanbay.hotel.room.domain.RoomType;
import com.hanbay.hotel.room.repository.RoomTypeRepository;
import com.hanbay.hotel.room.service.RoomAvailabilityCalculator;
import com.hanbay.hotel.user.domain.User;
import com.hanbay.hotel.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class BookingChangePricingTest {
    private static final LocalDate ARRIVAL = LocalDate.of(2026, 11, 20);
    private final BookingChangeRequestRepository requests = mock(BookingChangeRequestRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final RoomTypeRepository types = mock(RoomTypeRepository.class);
    private final BookingOrderPaymentService orders = mock(BookingOrderPaymentService.class);
    private final BookingSettlementLedger ledger = mock(BookingSettlementLedger.class);
    private final BookingAccessTokenService tokens = mock(BookingAccessTokenService.class);
    private final BookingCancellationPolicy cancellation = mock(BookingCancellationPolicy.class);
    private final PricingService pricing = mock(PricingService.class);
    private final RoomAvailabilityCalculator availability = mock(RoomAvailabilityCalculator.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AtomicLong priceIncrease = new AtomicLong();
    private BookingChangeService service;
    private BookingChangeRequest request;
    private List<Booking> scope;
    private Clock clock;
    private BookingOrder order;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void setup(int hoursBeforeArrival, int newNights, boolean moveArrival, long... prices) {
        setup(Duration.ofHours(hoursBeforeArrival), 2, newNights, moveArrival, prices);
    }

    private void setup(Duration beforeArrival, int oldNights, int newNights, boolean moveArrival, long... prices) {
        Instant arrival =
                ARRIVAL.atTime(14, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(arrival.minus(beforeArrival));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        HotelStayPolicy stay = new HotelStayPolicy(clock, new HotelStayProperties());
        service = new BookingChangeService(
                requests,
                bookings,
                types,
                orders,
                ledger,
                tokens,
                cancellation,
                stay,
                new BookingRescheduleCalculator(pricing, stay),
                availability,
                currentUser,
                users,
                clock);
        scope = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            RoomType type = RoomType.builder()
                    .id(UUID.randomUUID())
                    .name("Deluxe " + i)
                    .basePrice(prices[i])
                    .build();
            Booking booking = Booking.builder()
                    .id(UUID.randomUUID())
                    .bookingCode("BK-" + i)
                    .roomType(type)
                    .status(BookingStatus.CONFIRMED)
                    .checkInDate(ARRIVAL)
                    .checkOutDate(ARRIVAL.plusDays(oldNights))
                    .originalCheckInDate(ARRIVAL)
                    .originalFirstNightAmount(prices[i])
                    .termsVersion(UnifiedBookingPolicy.VERSION)
                    .acceptedTerms(UnifiedBookingPolicy.TERMS)
                    .totalAmount(prices[i] * oldNights)
                    .paidAmount(prices[i] * oldNights)
                    .build();
            for (int night = 0; night < oldNights; night++)
                booking.addNight(BookingNight.builder()
                        .stayDate(ARRIVAL.plusDays(night))
                        .pricePerNight(prices[i])
                        .build());
            scope.add(booking);
        }
        if (scope.size() > 1) {
            order = BookingOrder.builder()
                    .id(UUID.randomUUID())
                    .orderCode("ORDER-TEST")
                    .status(BookingOrderStatus.CONFIRMED)
                    .checkInDate(ARRIVAL)
                    .checkOutDate(ARRIVAL.plusDays(oldNights))
                    .build();
            scope.forEach(order::addBooking);
            when(bookings.findOrderIdForArrival("BK-0")).thenReturn(Optional.of(order.getId()));
            when(orders.lockOrder(order.getId())).thenReturn(order);
        } else {
            when(bookings.findOrderIdForArrival("BK-0")).thenReturn(Optional.empty());
            when(bookings.findRoomTypeIdByBookingCode("BK-0"))
                    .thenReturn(Optional.of(scope.get(0).getRoomType().getId()));
            when(types.findByIdForUpdate(any()))
                    .thenReturn(Optional.of(scope.get(0).getRoomType()));
            when(bookings.findByBookingCodeForUpdate("BK-0")).thenReturn(Optional.of(scope.get(0)));
        }
        LocalDate newArrival = moveArrival ? ARRIVAL.plusDays(5) : ARRIVAL;
        request = BookingChangeRequest.builder()
                .id(UUID.randomUUID())
                .booking(scope.get(0))
                .scopeKey(BookingChangeRequest.scopeOf(scope.get(0)))
                .type(BookingChangeRequest.Type.RESCHEDULE)
                .requestedCheckIn(newArrival)
                .requestedCheckOut(newArrival.plusDays(newNights))
                .reason("Khách thay đổi số đêm")
                .requestedAt(clock.instant())
                .build();
        when(requests.findBookingCodeById(request.getId())).thenReturn(Optional.of("BK-0"));
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        Payment payment = mock(Payment.class);
        when(ledger.lockPayments(anyList())).thenReturn(List.of(payment));
        when(ledger.applied(any(), eq(payment)))
                .thenAnswer(invocation -> ((Booking) invocation.getArgument(0)).getPaidAmount());
        when(ledger.available(any(), anyList()))
                .thenAnswer(invocation -> ((Booking) invocation.getArgument(0)).getPaidAmount());
        when(ledger.refundExcess(any(), anyList(), anyLong(), anyString(), anyString(), any(), isNull()))
                .thenAnswer(invocation -> Math.max(
                        ((Booking) invocation.getArgument(0)).getPaidAmount() - (long) invocation.getArgument(2), 0));
        when(pricing.calculateRoomPrice(any(), any(), any())).thenAnswer(invocation -> {
            RoomType type = invocation.getArgument(0);
            LocalDate start = invocation.getArgument(1), end = invocation.getArgument(2);
            List<BookingNight> nights = start.datesUntil(end)
                    .map(date -> BookingNight.builder()
                            .stayDate(date)
                            .pricePerNight(type.getBasePrice() + priceIncrease.get())
                            .build())
                    .toList();
            long amount =
                    nights.stream().mapToLong(BookingNight::getPricePerNight).sum();
            return new RoomPricingResult(nights, amount, 0, amount);
        });
        when(availability.countAvailableRoomsExcludingBooking(any(), any(), any(), any()))
                .thenReturn(10L);
        when(availability.countAvailableRoomsExcludingOrder(anySet(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Map<UUID, Long> counts = new HashMap<>();
                    scope.forEach(booking -> counts.put(booking.getRoomType().getId(), 10L));
                    return counts;
                });
        when(tokens.hash(anyString())).thenAnswer(invocation -> UUID.nameUUIDFromBytes(
                        ((String) invocation.getArgument(0)).getBytes(StandardCharsets.UTF_8))
                .toString());
        User manager = User.builder().id(UUID.randomUUID()).fullName("Quản lý").build();
        when(currentUser.currentUserId()).thenReturn(Optional.of(manager.getId()));
        when(users.findById(manager.getId())).thenReturn(Optional.of(manager));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "manager", "", List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))));
    }

    private BookingChangeCommand.Approve approve(String quoteToken) {
        return new BookingChangeCommand.Approve(
                quoteToken,
                null,
                "Khách chấp thuận báo giá ngoại lệ",
                true,
                false,
                Map.of(),
                clock.instant().plus(Duration.ofDays(1)));
    }

    @ParameterizedTest
    @CsvSource({"4320,0,6050000", "2880,0,6050000", "2682,50,9075000", "1440,50,9075000", "1439,100,12100000"})
    void twoCalendarDatesApartUsesExactHoursAndSeparatesExtraNightFromFee(
            int minutesBeforeArrival, int rate, long additional) {
        setup(Duration.ofMinutes(minutesBeforeArrival), 1, 2, true, 2_450_000, 3_600_000);
        var quote = service.quote(request.getId());
        assertThat(quote.oldTotalAmount()).isEqualTo(6_050_000);
        assertThat(quote.newTotalAmount()).isEqualTo(12_100_000);
        assertThat(quote.feeAmount()).isEqualTo(6_050_000L * rate / 100);
        assertThat(quote.additionalAmount()).isEqualTo(additional);
        assertThat(quote.rooms()).allSatisfy(room -> {
            assertThat(room.feeTiming().ratePercent()).isEqualTo(rate);
            assertThat(room.feeTiming().checkInAt()).isEqualTo(Instant.parse("2026-11-20T07:00:00Z"));
            assertThat(room.feeTiming().freeUntil()).isEqualTo(Instant.parse("2026-11-18T07:00:00Z"));
            assertThat(room.feeTiming().halfFeeUntil()).isEqualTo(Instant.parse("2026-11-19T07:00:00Z"));
        });
    }

    @Test
    void refreshingQuoteAfterTwentyFourHoursKeepsTheRateAtRequestTime() {
        setup(Duration.ofMinutes(2682), 1, 2, true, 2_450_000, 3_600_000);
        var before = service.quote(request.getId());
        Instant later = clock.instant().plus(Duration.ofHours(25));
        when(clock.instant()).thenReturn(later);
        var refreshed = service.quote(request.getId());
        assertThat(refreshed.feeAmount()).isEqualTo(3_025_000);
        assertThat(refreshed.rooms())
                .allSatisfy(room -> assertThat(room.feeTiming().ratePercent()).isEqualTo(50));
        assertThat(refreshed.quoteToken()).isEqualTo(before.quoteToken());
        assertThat(refreshed.serverNow()).isEqualTo(later);
    }

    @ParameterizedTest
    @CsvSource({"1,6050000", "3,18150000", "7,42350000"})
    void rescheduleFeeUsesOneFirstNightPerRoomRegardlessOfStayLengthAndNewRates(int oldNights, long oldTotal) {
        setup(Duration.ofHours(30), oldNights, 3, true, 2_450_000, 3_600_000);
        priceIncrease.set(1_000_000);
        var quote = service.quote(request.getId());
        assertThat(quote.oldTotalAmount()).isEqualTo(oldTotal);
        assertThat(quote.newTotalAmount()).isEqualTo(24_150_000);
        assertThat(quote.feeAmount()).isEqualTo(3_025_000);
        assertThat(quote.rooms())
                .extracting(room -> room.bookingCode(), room -> room.standardFee())
                .containsExactlyInAnyOrder(tuple("BK-0", 1_225_000L), tuple("BK-1", 1_800_000L));
    }

    @Test
    void servicesAffectStayBalanceWithoutBecomingPartOfFirstNightFeeBase() {
        setup(Duration.ofHours(30), 3, 3, true, 2_450_000, 3_600_000);
        priceIncrease.set(1_000_000);
        scope.forEach(booking -> {
            booking.addAddOn(BookingAddOn.builder()
                    .addOnService(AddOnService.builder()
                            .id(UUID.randomUUID())
                            .name("Đưa đón")
                            .build())
                    .quantity(1)
                    .unitPrice(900_000)
                    .totalPrice(900_000)
                    .build());
            booking.applyTotalAmount(booking.getTotalAmount() + 900_000);
        });
        var quote = service.quote(request.getId());
        assertThat(quote.oldTotalAmount()).isEqualTo(19_950_000);
        assertThat(quote.newTotalAmount()).isEqualTo(25_950_000);
        assertThat(quote.availableAmount()).isEqualTo(18_150_000);
        assertThat(quote.feeAmount()).isEqualTo(3_025_000);
        assertThat(quote.additionalAmount()).isEqualTo(10_825_000);
    }

    @Test
    void quotesThreeRoomsGoingFromTwoToThreeNightsBeforeManagerException() {
        setup(12, 3, true, 2_450_000, 2_500_000, 2_500_000);
        var quote = service.quote(request.getId());
        assertThat(quote.pricingAvailable()).isTrue();
        assertThat(quote.canApprove()).isFalse();
        assertThat(quote.blockReason()).contains("số đêm");
        assertThat(quote.newTotalAmount()).isEqualTo(22_350_000);
        assertThat(quote.feeAmount()).isEqualTo(7_450_000);
        assertThat(quote.availableAmount()).isEqualTo(14_900_000);
        assertThat(quote.additionalAmount()).isEqualTo(14_900_000);
        assertThat(quote.refundAmount()).isZero();
        assertThat(scope)
                .allSatisfy(booking -> assertThat(booking.getCheckOutDate()).isEqualTo(ARRIVAL.plusDays(2)));
    }

    @Test
    void shorteningNearArrivalCanHaveNoRefundBecauseFirstNightFeeOffsetsTheRemovedNight() {
        setup(12, 1, true, 2_450_000, 2_500_000, 2_500_000);
        var quote = service.quote(request.getId());
        assertThat(quote.pricingAvailable()).isTrue();
        assertThat(quote.newTotalAmount()).isEqualTo(7_450_000);
        assertThat(quote.feeAmount()).isEqualTo(7_450_000);
        assertThat(quote.additionalAmount()).isZero();
        assertThat(quote.refundAmount()).isZero();
    }

    @Test
    void freeShorteningQuotesTheFullUnusedBalanceAsRefund() {
        setup(72, 1, false, 2_450_000, 2_500_000, 2_500_000);
        var quote = service.quote(request.getId());
        assertThat(quote.feeAmount()).isZero();
        assertThat(quote.refundAmount()).isEqualTo(7_450_000);
        assertThat(quote.additionalAmount()).isZero();
    }

    @Test
    void extraNightsWithSameArrivalKeepExistingPricesAndHaveNoExtraChangeFee() {
        setup(12, 3, false, 1_000_000);
        priceIncrease.set(200_000);
        var quote = service.quote(request.getId());
        assertThat(quote.newTotalAmount()).isEqualTo(3_200_000);
        assertThat(quote.feeAmount()).isZero();
        assertThat(quote.additionalAmount()).isEqualTo(1_200_000);
        assertThat(quote.rooms().get(0).nights())
                .extracting(night -> night.price())
                .containsExactly(1_000_000L, 1_000_000L, 1_200_000L);
        verify(pricing).calculateRoomPrice(any(), eq(ARRIVAL.plusDays(2)), eq(ARRIVAL.plusDays(3)));
    }

    @Test
    void variableNightsRequireExplicitExceptionThenApplyAtomicallyWithoutChangingOriginalTerms() {
        setup(12, 3, true, 2_450_000, 2_500_000, 2_500_000);
        var before = service.quote(request.getId());
        assertThatThrownBy(() -> service.approve(request.getId(), approve(before.quoteToken())))
                .isInstanceOf(StayModificationException.class);
        var allowed = service.allowException(request.getId(), "Chấp thuận tăng một đêm cho toàn đơn");
        assertThat(allowed.canApprove()).isTrue();
        assertThat(allowed.quoteToken()).isNotEqualTo(before.quoteToken());
        var result = service.approve(request.getId(), approve(allowed.quoteToken()));
        assertThat(result.status()).isEqualTo(BookingChangeRequest.Status.APPROVED);
        assertThat(result.snapshot())
                .containsEntry("additionalAmount", 14_900_000L)
                .containsEntry("changeTermsVersion", BookingRescheduleCalculator.TERMS_VERSION);
        assertThat(scope).allSatisfy(booking -> {
            assertThat(booking.getCheckInDate()).isEqualTo(ARRIVAL.plusDays(5));
            assertThat(booking.getNights()).hasSize(3);
            assertThat(booking.getPolicyFeeTotal()).isEqualTo(booking.getOriginalFirstNightAmount());
            assertThat(booking.getTotalAmount()).isEqualTo(booking.getOriginalFirstNightAmount() * 4);
            assertThat(booking.getAcceptedTerms()).isEqualTo(UnifiedBookingPolicy.TERMS);
        });
        // The order keeps its original booked amount; its response totals the current room balances.
        assertThat(order.getTotalAmount()).isEqualTo(14_900_000);
        assertThat(scope.stream().mapToLong(Booking::getTotalAmount).sum()).isEqualTo(29_800_000);
        assertThat(service.approve(request.getId(), approve(allowed.quoteToken()))
                        .id())
                .isEqualTo(result.id());
        verify(ledger, times(3)).refundExcess(any(), anyList(), anyLong(), anyString(), anyString(), any(), isNull());
    }

    @Test
    void approvingShorteningCreatesTheQuotedRefundAndPreservesOriginalFeeBase() {
        setup(72, 1, false, 1_000_000);
        var quote = service.allowException(request.getId(), "Khách rút ngắn một đêm");
        var result = service.approve(request.getId(), approve(quote.quoteToken()));
        assertThat(result.snapshot()).containsEntry("refundAmount", 1_000_000L).containsEntry("additionalAmount", 0L);
        assertThat(scope.get(0).getTotalAmount()).isEqualTo(1_000_000);
        assertThat(scope.get(0).getPaidAmount()).isEqualTo(1_000_000);
        assertThat(scope.get(0).getOriginalFirstNightAmount()).isEqualTo(1_000_000);
        assertThat(scope.get(0).getNights()).hasSize(1);
    }

    @Test
    void inventoryFailureStillShowsPriceButCannotBeOverriddenByFinancialConsent() {
        setup(12, 3, true, 1_000_000);
        when(availability.countAvailableRoomsExcludingBooking(any(), any(), any(), any()))
                .thenReturn(0L);
        var quote = service.allowException(request.getId(), "Xin xét tăng số đêm");
        assertThat(quote.pricingAvailable()).isTrue();
        assertThat(quote.canApprove()).isFalse();
        assertThat(quote.blockReason()).contains("không đủ phòng");
        assertThatThrownBy(() -> service.approve(request.getId(), approve(quote.quoteToken())))
                .isInstanceOf(StayModificationException.class);
        assertThat(scope.get(0).getNights()).hasSize(2);
    }

    @Test
    void unreconciledPaymentsDoNotProduceAFakeQuoteUsingTheOldStayTotal() {
        setup(12, 3, true, 1_000_000);
        when(ledger.applied(any(), any())).thenReturn(0L);
        var quote = service.quote(request.getId());
        assertThat(quote.pricingAvailable()).isFalse();
        assertThat(quote.feeAmount()).isNull();
        assertThat(quote.refundAmount()).isNull();
        assertThat(quote.canApprove()).isFalse();
        assertThat(quote.blockReason()).contains("đối soát");
    }

    @Test
    void priceChangesRequireARevisedQuoteBeforeAnyFinancialOrInventoryMutation() {
        setup(12, 3, true, 1_000_000);
        var quote = service.allowException(request.getId(), "Xét tăng đêm");
        priceIncrease.set(100_000);
        assertThatThrownBy(() -> service.approve(request.getId(), approve(quote.quoteToken())))
                .isInstanceOf(StayModificationException.class);
        assertThat(scope.get(0).getCheckInDate()).isEqualTo(ARRIVAL);
        verify(ledger, never()).refundExcess(any(), anyList(), anyLong(), anyString(), anyString(), any(), any());
    }
}
