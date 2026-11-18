package com.hanbay.hotel.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hanbay.hotel.booking.config.HotelStayProperties;
import com.hanbay.hotel.booking.domain.Booking;
import com.hanbay.hotel.booking.domain.BookingNight;
import com.hanbay.hotel.booking.domain.pricing.BookingNightPriceRule;
import com.hanbay.hotel.booking.exception.BookingValidationException;
import com.hanbay.hotel.booking.policy.HotelStayPolicy;
import com.hanbay.hotel.booking.policy.UnifiedBookingPolicy;
import com.hanbay.hotel.pricing.service.PricingService;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BookingRescheduleCalculatorTest {
    private final LocalDate arrival = LocalDate.of(2026, 11, 18);
    private final HotelStayProperties properties = new HotelStayProperties();
    private final PricingService pricing = mock(PricingService.class);
    private final BookingRescheduleCalculator calculator =
            new BookingRescheduleCalculator(pricing, new HotelStayPolicy(Clock.systemUTC(), properties));

    private Booking booking(long originalP, long previousFee, long removedNight) {
        Booking booking = Booking.builder()
                .checkInDate(arrival)
                .checkOutDate(arrival.plusDays(2))
                .termsVersion(UnifiedBookingPolicy.VERSION)
                .originalFirstNightAmount(originalP)
                .policyFeeTotal(previousFee)
                .build();
        booking.addNight(BookingNight.builder()
                .stayDate(arrival)
                .pricePerNight(1_000_000)
                .build());
        booking.addNight(BookingNight.builder()
                .stayDate(arrival.plusDays(1))
                .pricePerNight(removedNight)
                .build());
        return booking;
    }

    private Instant requested(int hours) {
        return arrival.atTime(14, 0).atZone(properties.zoneId()).toInstant().minus(Duration.ofHours(hours));
    }

    @ParameterizedTest
    @CsvSource({
        "72,0,0",
        "48,0,0",
        "30,0,900000",
        "24,0,900000",
        "12,0,1000000",
        "72,500000,500000",
        "30,1000000,1000000"
    })
    void partialCancellationUsesFirstRemovedSnapshotAndCapsTotalFeesWithoutResettingHistory(
            int hours, long previous, long expected) {
        var result = calculator.calculate(
                booking(1_000_000, previous, 1_800_000), arrival, arrival.plusDays(1), requested(hours));
        assertThat(result.roomTotal()).isEqualTo(1_000_000);
        assertThat(result.policyFee()).isEqualTo(expected);
        assertThat(result.nights()).hasSize(1);
        verifyNoInteractions(pricing);
    }

    @Test
    void partialCancellationRoundsDownAndDoesNotReplaceAZeroOriginalCap() {
        assertThat(calculator
                        .calculate(booking(1_000_000, 0, 1_000_001), arrival, arrival.plusDays(1), requested(30))
                        .policyFee())
                .isEqualTo(500_000);
        assertThat(calculator
                        .calculate(booking(0, 0, 1_000_000), arrival, arrival.plusDays(1), requested(12))
                        .policyFee())
                .isZero();
    }

    @Test
    void retainedSnapshotsAndRuleAmountsAreCopiedWithoutMutatingTheCurrentBooking() {
        Booking booking = booking(1_000_000, 0, 1_000_000);
        booking.getNights()
                .get(0)
                .addAppliedRule(
                        BookingNightPriceRule.builder().adjustmentAmount(50_000).build());
        var result = calculator.calculate(booking, arrival, arrival.plusDays(1), requested(72));
        assertThat(result.nights().get(0)).isNotSameAs(booking.getNights().get(0));
        assertThat(result.nights().get(0).getAppliedRules().get(0).getAdjustmentAmount())
                .isEqualTo(50_000);
        assertThat(result.nights().get(0).getBooking()).isNull();
        assertThat(booking.getNights()).hasSize(2);
    }

    @Test
    void missingSavedPricesRequireReconciliationInsteadOfSilentlyRepricingTheOldNights() {
        Booking booking = booking(1_000_000, 0, 1_000_000);
        booking.getNights().clear();
        assertThatThrownBy(() -> calculator.calculate(booking, arrival, arrival.plusDays(1), requested(72)))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("Thiếu giá đêm");
    }
}
